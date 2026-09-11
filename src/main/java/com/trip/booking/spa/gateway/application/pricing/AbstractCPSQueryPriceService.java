package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 刷价骨架：一份实现，各家共用（docs/price-refresh.md §1.1）。
 *
 * <p>骨架管这些：分布式锁互斥、<b>连续消费直到关闸</b>、取批、行级并发、单行 try 隔离、
 * 借入与复位、三态计数、轮次日志、指标（含轮内进度）、不截断等待。各家只答四个问题：
 * 取哪一批、按哪些维度刷、怎么刷一行、刷完怎么记账。
 *
 * <p><b>为什么改成连续循环</b>（2026-08-25，取代 F-2.2 原来的「一轮一返回」）：cron 每 10 分钟
 * 才给一次活，而一轮实测 5.5 分钟，于是 45% 的时间在空等——刷一遍要 54 分钟，其中 24 分钟纯闲着。
 * 原规则禁止常驻循环的理由是「关闸最迟要等进程重启才生效」，那说的是<b>不看闸的</b>死循环；
 * 本骨架每轮之间重新读一次闸（{@link #gateOpen()} 走 Environment 实时读，Nacos 热生效），
 * 关闸延迟 = 一轮时长，与原来「关闸延迟 = 一个 cron 周期」同量级，而空等归零。
 *
 * <p><b>cron 没有被删，它从节拍器变成看门狗</b>：循环跑着时 cron 触发会被供应商专属执行器
 * 拒掉（单线程 + SynchronousQueue，F-2.7 忙则跳过）；循环若因意外退出，下一个 cron 点把它
 * 重新拉起。这是保留 cron 的唯一理由，也是它现在的全部职责。
 *
 * <p><b>代价</b>：F-2.7 原本靠「忙则跳过」这个可见事件暴露「慢」，连续循环里慢只是慢、不再产生
 * 跳过事件。该信号改由轮次日志的耗时/实际速率与 {@code refresh_inflight_*} 两个 gauge 承载。
 *
 * @param <T> 各家的任务行实体
 */
@Slf4j
public abstract class AbstractCPSQueryPriceService<T extends RefreshTaskRow> {

    /** 一轮取到 0 行即认为本档暂时没活；所有档都没活就退出循环，等下一个 cron 点 */
    private static final boolean NO_WORK = false;
    private static final boolean DID_WORK = true;

    /**
     * 单行耗时均值，只用于估算「多久没跑完该报告一声」，<b>不用于截断</b>。
     * 取艺龙 2026-08-19 生产实测均值 1.45s 向上取整；不取 p90——并发下的整轮吞吐由均值决定。
     */
    private static final double PER_CALL_SEC_MEAN = 1.5;

    @javax.annotation.Resource
    private com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService priceCacheService;

    protected abstract RedissonClient redissonClient();

    /** 与手动入口共用的锁键（§3.8.2 一事一闸） */
    protected abstract String lockKey();

    protected abstract SupplierSourceEnum supplier();

    /** 闸口，每轮重新读——关闸即停做功（§3.8.3） */
    protected abstract boolean gateOpen();

    /** 本次派发要依次跑的档位序列（如成交→高频→常规→远期） */
    protected abstract List<Integer> tiers();

    /** 该档的取批上限 */
    protected abstract int batchSize(int priority);

    /** 该档的行级并发度。兜底须取安全侧（串行）——并发在连接池未同步放大时会与出价抢连接 */
    protected abstract int concurrency(int priority);

    /** 该档的速率（仅用于轮次日志与耗时预估；真正的限流在通道层） */
    protected abstract double declaredQps(int priority);

    protected abstract List<T> nextBatch(int priority, int temporaryUpgrade, int batchSize);

    /**
     * 一行要按哪些维度各刷一次（如占用人数）。返回的每个元素都会调一次
     * {@link #refreshOne}，故它直接决定本轮的调用次数 = 行数 × 维度数。
     */
    protected abstract List<String> dimensions();

    /** 刷一行的一个维度。实现方只需返回三态，不必自己计数或落日志 */
    protected abstract RefreshOutcome refreshOne(T row, String dimension);

    /**
     * 供应商侧的"今天"用哪个时区算入住日。默认 JVM 默认时区；
     * 供应商有明确口径的必须覆盖（艺龙与飞猪均为 Asia/Shanghai）。
     */
    protected ZoneId supplierZone() {
        return ZoneId.systemDefault();
    }

    /**
     * 刷一行的一次查价。<b>只查不写</b>——写缓存与三态映射由 {@link #refreshViaQuery} 统一做。
     * 默认抛异常：只有走 {@code refreshViaQuery} 的实现才需要它（Expedia 刷价出口另有形状，
     * 见其 {@code refreshOne}）。
     */
    protected PricingResult queryForRefresh(PriceQuery request) {
        throw new UnsupportedOperationException("未实现 queryForRefresh：该家的 refreshOne 应自建流程");
    }

    /**
     * 刷一行一个维度的标准走法：组装请求 → 查一次 → 写缓存 → 给三态。
     *
     * <p>此前这三件事在每家的 {@code refreshOne} 与 {@code queryPricesCache} 里各写一份
     * （2026-09-07 前艺龙与飞猪逐字相同、Expedia 另有形状），而它们换一家供应商完全不用改
     * （§4.1.3 首要判据）。各家现在只答一个问题：这一次查价的结果是什么
     * （{@link #queryForRefresh}）。
     *
     * <p><b>roomNum 恒 1 是已验证的选择</b>：缓存键不含间数、缓存价也是单间口径，多间在
     * 验价与下单侧乘间数（艺龙 H001188）。2026-08-24 生产 A/B 实测 hotel.detail 不按
     * NumberOfRooms 过滤可售集合与单间价；FAQ 337 亦只要求 NumberOfAdults 与 ChildAges
     * 与 detail 一致，未提间数。
     *
     * <p>三态口径：INDETERMINATE → FAILED 且<b>不动缓存</b>（F-5.1，一次抖动不许清在售价）；
     * 空列表照走 {@code productToCache}（F-5.2 落无货标记、清僵尸价 B7）。
     */
    protected final RefreshOutcome refreshViaQuery(T row, String dimension) {
        LocalDate today = LocalDate.now(supplierZone());
        // 供应商坐标就在指令里：刷价不再构造「不带供应商的请求」——那个形状是
        // PriceCacheService javadoc 记的 2026-08-20 事故之源，也是 #237 的温床
        PriceQuery request = PriceQuery.builder()
                .supplierId(supplier().getCode())
                .supplierHotelId(row.getShId())
                .adultNum(Integer.parseInt(dimension)).childNum(0)
                .childAges(new ArrayList<>())
                .checkIn(today.plusDays(row.getDelayCheckIn()).toString())
                .checkOut(today.plusDays(row.getDelayCheckOut()).toString())
                .roomNum(1).build();

        PricingResult result = queryForRefresh(request);
        if (result == null || result.outcome() == PricingOutcome.INDETERMINATE) {
            return RefreshOutcome.FAILED;
        }
        List<Product> products = result.products();
        priceCacheService.productToCache(products, request);
        return products.isEmpty() ? RefreshOutcome.EMPTY : RefreshOutcome.ON_SALE;
    }

    /** 记账：写回刷价时间与次数（各家 mapper 不同，且 Expedia 还要按天重置计数） */
    protected abstract void markRefreshed(T row);

    /**
     * 行刷完后的调档钩子，默认不动档。入参是该行全维度的聚合结果：任一维度在售
     * 即 ON_SALE；全空即 EMPTY；有失败且无在售即 FAILED——<b>失败不许当无货调档</b>
     * （F-5.1 同款纪律：一次网络抖动不该把店打进慢车道）。
     */
    protected void adjustPriority(T row, RefreshOutcome outcome) {
    }

    /**
     * 无货偏移编码的统一档位算法（艺龙/飞猪共用，"形式统一分配差异"）：
     * 业务档 N（0..9，如住期远近 0=T0-2/1=T3-7/2=T8-30），其无货态=N+{@link #SOLD_OUT_OFFSET}。
     * 无货与业务档<b>正交</b>——成交/近期店也会暂时满房，压进同一序列会丢原档；
     * 偏移让原档藏在数值里：无货 +10 沉入本档无货位，刷出有货 -10 回自己的业务档，
     * 无需记忆列。FAILED 返回原档（不动）。调用方对返回值 != 当前档时写库。
     */
    protected static int soldOutOffsetTarget(int current, RefreshOutcome outcome) {
        if (outcome == RefreshOutcome.FAILED) {
            return current;
        }
        if (outcome == RefreshOutcome.ON_SALE) {
            return current >= SOLD_OUT_OFFSET ? current - SOLD_OUT_OFFSET : current;
        }
        return current < SOLD_OUT_OFFSET ? current + SOLD_OUT_OFFSET : current;
    }

    /** 无货位偏移。0-9 留给业务档（含艺龙的人工停用位 9,它不进 tiers 故不受调档触及） */
    protected static final int SOLD_OUT_OFFSET = 10;

    /** 三态。与 PricingOutcome 同义，但刷价只关心这三种，不引入契约层的枚举 */
    public enum RefreshOutcome {
        /** 有在售产品，已落缓存 */
        ON_SALE,
        /** 供应商明确答无在售，已落空标记（F-5.2） */
        EMPTY,
        /** 没问出结果——频控/超时/解析失败，不动缓存（F-5.1） */
        FAILED
    }

    /**
     * 拿锁，然后<b>连续</b>消费到关闸或没活。定时入口与手动入口都走这里。
     *
     * @return true=正常跑完（含"没活"）；false=没拿到锁
     */
    public final boolean runUntilIdleOrClosed(String trigger) {
        RLock lock = redissonClient().getLock(lockKey());
        if (!lock.tryLock()) {
            log.info("[gate] {} 已被其他执行者持有，本次跳过, trigger={}", lockKey(), trigger);
            return false;
        }
        long start = System.currentTimeMillis();
        int rounds = 0;
        try {
            // 每轮之间重新读闸：关掉 Nacos 开关后，最迟一轮内停止做功
            while (gateOpen()) {
                boolean didWork = NO_WORK;
                for (int priority : tiers()) {
                    didWork |= runOneRound(priority, borrowFor(priority), trigger);
                    rounds++;
                }
                if (!didWork) {
                    // 所有档都没活了。不空转——退出，等下一个 cron 点再来看
                    log.info("{} 各档均无待刷任务，本次派发结束, trigger={}, 已跑 {} 轮, 耗时 {} ms",
                            logPrefix(), trigger, rounds, System.currentTimeMillis() - start);
                    return true;
                }
            }
            log.info("[gate] {} 闸已关，停止做功, trigger={}, 已跑 {} 轮, 耗时 {} ms",
                    logPrefix(), trigger, rounds, System.currentTimeMillis() - start);
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 该档取批时是否借入升档行。默认只有档 0 借入（F-2.4） */
    protected int borrowFor(int priority) {
        return priority == 0 ? 1 : 0;
    }

    protected String logPrefix() {
        return supplier().name().toLowerCase() + "QueryPriceTask";
    }

    private boolean runOneRound(int priority, int temporaryUpgrade, String trigger) {
        long roundStart = System.currentTimeMillis();
        int batchSize = batchSize(priority);
        int concurrency = Math.max(1, concurrency(priority));
        List<String> dims = dimensions();
        List<T> list = nextBatch(priority, temporaryUpgrade, batchSize);

        log.info("{} 本轮开始, trigger={}, priority={}, 取到 {} 行 × 维度{}, batchSize={}, qps={}, 并发={}",
                logPrefix(), trigger, priority, list.size(), dims, batchSize, declaredQps(priority), concurrency);
        if (CollectionUtils.isEmpty(list)) {
            return NO_WORK;
        }

        Map<String, Object> tags = new HashMap<>(2);
        tags.put(MetricTags.SUPPLIER, supplier().name());
        tags.put("priority", String.valueOf(priority));
        int totalCalls = list.size() * dims.size();
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_SIZE, tags, totalCalls);
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, tags, 0);

        AtomicInteger onSale = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        // 借入与复位各自计数（§6.2.1）：借入持续不为 0 而复位长期为 0，即是复位失效的信号（issue #95）
        AtomicInteger borrowed = new AtomicInteger();
        AtomicInteger demoted = new AtomicInteger();
        ExecutorService pool = ThreadPools.fixed(
                supplier().name().toLowerCase() + "-refresh-p" + priority, concurrency, true);
        for (T row : list) {
            pool.execute(() -> {
                try {
                    if (1 == row.getTemporaryUpgrade() && priority != row.getPriorityLevelNumber()) {
                        borrowed.incrementAndGet();
                    }
                    Date deadline = row.getUpgradeDeadline();
                    if (null == deadline || !deadline.after(new Date())) {
                        if (1 == row.getTemporaryUpgrade()) {
                            demoted.incrementAndGet();
                        }
                        row.setTemporaryUpgrade(0);
                    }
                    markRefreshed(row);
                    boolean anyOnSale = false;
                    boolean anyFailed = false;
                    for (String dim : dims) {
                        RefreshOutcome one = refreshOne(row, dim);
                        anyOnSale |= one == RefreshOutcome.ON_SALE;
                        anyFailed |= one == RefreshOutcome.FAILED;
                        switch (one) {
                            case ON_SALE -> onSale.incrementAndGet();
                            case EMPTY -> empty.incrementAndGet();
                            case FAILED -> failed.incrementAndGet();
                            default -> failed.incrementAndGet();
                        }
                        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, tags,
                                onSale.get() + empty.get() + failed.get());
                    }
                    adjustPriority(row, anyOnSale ? RefreshOutcome.ON_SALE
                            : anyFailed ? RefreshOutcome.FAILED : RefreshOutcome.EMPTY);
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("{} 单行异常, shId={}", logPrefix(), row.getShId(), e);
                }
            });
        }
        // 等它跑完，<b>不设截断</b>：单行已被 HTTP 超时与有限重试封死，故一轮必然收敛；
        // 而"跑太久"本来就有正确的处理（下一次 cron 被跳过），不需要在这里另造一套。
        // 曾经自造「等待预算 + shutdownNow」，按配额算预算却隐含假设总能跑到配额，
        // 2026-08-19 连续三轮各丢 41% 的行，且那些行的 last_time 已提前更新会被当成刚刷过而饿死。
        pool.shutdown();
        try {
            long expected = Math.max(60, estimateRoundSeconds(totalCalls, declaredQps(priority), concurrency));
            while (!pool.awaitTermination(expected, TimeUnit.SECONDS)) {
                int done = onSale.get() + empty.get() + failed.get();
                log.warn("{} 本轮超出预期仍在继续（不截断）, trigger={}, priority={}, 已处理={}/{}, 预期={}s",
                        logPrefix(), trigger, priority, done, totalCalls, expected);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            log.warn("{} 本轮被中断（容器关闭）, trigger={}, priority={}, 已处理={}/{}",
                    logPrefix(), trigger, priority, onSale.get() + empty.get() + failed.get(), totalCalls);
        }

        long cost = System.currentTimeMillis() - roundStart;
        Monitor.recordMany(MetricNames.REFRESH_ROWS, tags, totalCalls);
        Monitor.recordMany(MetricNames.REFRESH_ONSALE, tags, onSale.get());
        Monitor.recordMany(MetricNames.REFRESH_EMPTY, tags, empty.get());
        Monitor.recordMany(MetricNames.REFRESH_FAILED, tags, failed.get());
        Monitor.recordMany(MetricNames.REFRESH_BORROWED, tags, borrowed.get());
        Monitor.recordMany(MetricNames.REFRESH_DEMOTED, tags, demoted.get());
        Monitor.recordTime(MetricNames.REFRESH_ROUND, tags, cost);
        // 轮末归零：留着终值会让面板在两轮之间显示「已处理=满」，看着像一直在跑
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, tags, 0);

        log.info("{} 本轮结束, trigger={}, priority={}, 共 {} 行 / {} 次调用, 有在售={}, 无在售={}, 失败={}, "
                        + "借入={}, 复位={}, 并发={}, 实际 {} 次/秒, 耗时 {} ms",
                logPrefix(), trigger, priority, list.size(), totalCalls, onSale.get(), empty.get(), failed.get(),
                borrowed.get(), demoted.get(), concurrency,
                cost > 0 ? String.format("%.2f", totalCalls * 1000.0 / cost) : "-", cost);
        return DID_WORK;
    }

    /**
     * 本轮<b>预计</b>耗时（秒），只用来决定「多久还没跑完就该报告一声」。
     *
     * <p>取 {@code min(配额, 并发能力)}：只按配额算会隐含假设总能跑到配额，而并发度配得低时跑不到
     * （2026-08-19 实证：配额 4、并发 3，实际 1.82）。
     */
    static long estimateRoundSeconds(int calls, double qps, int concurrency) {
        double capacity = Math.max(concurrency, 1) / PER_CALL_SEC_MEAN;
        double effective = Math.min(Math.max(qps, 0.1), capacity);
        return (long) Math.ceil(calls / effective);
    }
}
