package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 艺龙刷价：消费 {@code elong_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>结构照搬 {@code ExpediaCPSQueryPriceServiceImpl}（一轮一返回、Redisson 锁互斥、
 * 单行 try 隔离坏数据），差异全部来自艺龙的两条硬约束：
 *
 * <ul>
 *   <li><b>逐店查询</b>：一行任务 = 一次 hotel.detail 调用，不批量聚合（移植风险⑤）</li>
 *   <li><b>失败分类计数</b>：频控命中与真实业务错误必须分开看，否则调速没有依据（F-8.2）</li>
 * </ul>
 *
 * <p><b>速率不在本类</b>：刷价这条路声明 {@link CallPurpose#REFRESH}，通道层据此扣
 * {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH}（用途桶）与接口桶各一格，
 * 并按后台语义阻塞排队。取值只在 Nacos 的 {@code ratelimit.qps}。
 *
 * <p><b>提速的两条上限</b>（2026-08-19 实测后并发化）：
 * <ol>
 *   <li><b>用途桶额度</b>：{@code hotel.detail} 由刷价、点订前的现取现验、上游实时查价三路共用，
 *       各占一个用途桶，之和不得超过接口桶（后者是对艺龙的承诺）；</li>
 *   <li><b>并发度 ≈ 目标 QPS × 单行耗时</b>。给太小则限流器空转、跑不到目标；给太大则线程都堆在
 *       等许可上。上调前看连接池——并发行与出价链路共用 Redis 与 DB，而出价是真实客流。</li>
 * </ol>
 *
 * <p>本类不决定"何时刷"——cron 在 {@code ElongCPSQueryPriceTask}，覆盖全时段；
 * 其前置是 cursor 侧已停刷艺龙，否则两边抢额度双输（2026-08-19 已实测确认 cursor 对艺龙
 * HTTP 调用为 0，全部 elong 日志均来自消费 SPA 的装配器）。
 */
@Slf4j
@Service
public class ElongCPSQueryPriceServiceImpl implements ElongCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:elong:cps:query-price";

    /**
     * 刷价的用途桶键。<b>本类不再手写 acquire</b>——用途由 {@link CallPurpose#REFRESH} 声明、
     * 通道层扣格（§3.3 限流一律走统一限流）。这里只读它的生效值打进轮次日志：那行是唯一的
     * 轮次事实来源，而这个值决定本轮时长。
     *
     * <p>改动前是业务代码手写一行 {@code acquire}，全仓仅此一处——用途桶不是接口维度的键，
     * 没有任何机制保证新增的 detail 调用路径会记得写那一行，忘写即静默绕过分配。
     */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ElongQueryPriceTaskMapper elongQueryPriceTaskMapper;

    @Resource
    private ElongPriceService elongPriceService;

    @Resource
    private Environment environment;

    /** 只读刷价子配额的生效值用于日志与护栏；限流动作本身走 RateLimitHolder（§3.3） */
    @Resource
    private com.trip.booking.spa.platform.ratelimit.RateLimitProperties rateLimitProperties;

    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            log.info("[gate] {} 已被其他执行者持有，本次跳过, trigger={}", LOCK_KEY, trigger);
            return false;
        }
        try {
            return runOneRound(priority, temporaryUpgrade, trigger);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Boolean runOneRound(int priority, int temporaryUpgrade, String trigger) {
        long roundStart = System.currentTimeMillis();
        // 刷价速率不在本类配置——它是统一限流的一个子配额（REFRESH_LIMIT_KEY），
        // 取值只在 Nacos 的 ratelimit.qps 里。这里只把生效值读出来打进轮次日志，
        // 因为它决定本轮时长，而"轮次结束"那行是唯一的轮次事实来源。
        double qps = rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
        // 分档独立键(批次3):不复用旧 batch-size/qps——生产旧值(500/1.0)是单档时代
        // 的节奏,直接继承会让两档总时长超出 10 分钟 cron 而回到跳轮状态
        int batchSize = priority == 3
                ? environment.getProperty("task.elong-cps.far-batch-size", Integer.class, 200)
                : priority == 2
                ? environment.getProperty("task.elong-cps.deal-batch-size", Integer.class, 1500)
                : priority == 0
                ? environment.getProperty("task.elong-cps.high-batch-size", Integer.class, 400)
                : environment.getProperty("task.elong-cps.normal-batch-size", Integer.class, 200);
        // 行级并发度。串行时每行 1.45s 均值(p50 1.08 / p90 3.13 / p99 4.51,2026-08-19 生产实测),
        // 于是实际只跑出 0.89 QPS——连当时设的 2 QPS 限额都摸不到,限流器根本不是瓶颈。
        // 要真跑到目标 QPS,并发度须 ≈ QPS × 单行耗时;取值别超连接池(见类注释"三条上限")。
        // 兜底取 1(串行)是安全侧(§3.3.3):并发化是新引入的能力,Nacos 读不到时应退回
        // <b>已知安全的旧行为</b>,而不是静默采用新行为。串行只是慢,而并发在连接池配置同时
        // 未生效的情况下会与出价抢连接——出价是真实客流。生产运行值(高频 6 / 常规 3)在 Nacos。
        int concurrency = priority == 3
                ? environment.getProperty("task.elong-cps.far-concurrency", Integer.class, 6)
                : priority == 2
                ? environment.getProperty("task.elong-cps.deal-concurrency", Integer.class, 6)
                : priority == 0
                ? environment.getProperty("task.elong-cps.high-concurrency", Integer.class, 1)
                : environment.getProperty("task.elong-cps.normal-concurrency", Integer.class, 1);
        // 用途桶未登记时，通道层只扣接口桶（isRegistered 判定），刷价会按接口桶的速率跑——
        // 那是"刷价可以吃满对艺龙的整个承诺"，客流一格不留。不改速率、只喊出来：
        // 越界的判定与告警归 RateLimitProperties（子桶之和 ≤ 接口桶），这里管的是"压根没登记"。
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，本轮将按接口桶 {} QPS 跑满，不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            qps = interfaceQps;
        }
        // 每行按这些占用各查一次：高德标准间按 2 人问价，只刷 1 人时 2 人查询如实拿空，
        // 曝光刷新断粮 16h 后被 RATE_DEAD 整体撤下（2026-08-22 事故）
        List<Integer> occupancyAdults = Arrays.stream(environment
                        .getProperty("task.elong-cps.occupancies", "1").split(","))
                .map(String::trim).filter(v -> !v.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toList());
        List<ElongQueryPriceTask> list = elongQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("elongQueryPriceTask 本轮开始, trigger={}, priority={}, 取到 {} 行 × 占用{}, batchSize={}, qps={}, 并发={}",
                trigger, priority, list.size(), occupancyAdults, batchSize, qps, concurrency);
        if (CollectionUtils.isEmpty(list)) {
            log.info("elongQueryPriceTask 本轮结束, trigger={}, 无待刷任务, 耗时 {} ms",
                    trigger, System.currentTimeMillis() - roundStart);
            return true;
        }

        // 轮内进度（O-3.9）：其余 refresh_* 都在轮末一次上报，于是一轮 9 分钟里面板是平的，
        // 短窗口看 rate 会落在两轮之间画成 0，看着像没在跑。这两个 gauge 给出「已处理/共」，
        // 调速时不必等一轮结束就能看出效果
        Map<String, Object> roundTags = new HashMap<>(2);
        roundTags.put(MetricTags.SUPPLIER, SupplierSourceEnum.ELONG.name());
        roundTags.put("priority", String.valueOf(priority));
        int totalCalls = list.size() * occupancyAdults.size();
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_SIZE, roundTags, totalCalls);
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, roundTags, 0);

        AtomicInteger onSale = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        // 借入与复位各自计数（§6.2.1）：这两个走向此前无任何日志落点，issue #95 的孤儿行
        // 正因如此积累了一整天无人察觉。借入数持续不为 0 而复位数长期为 0，即是复位失效的信号
        AtomicInteger borrowed = new AtomicInteger();
        AtomicInteger demoted = new AtomicInteger();
        AtomicInteger seq = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "elong-refresh-p" + priority + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (ElongQueryPriceTask task : list) {
            pool.execute(() -> {
            try {
                if (1 == task.getTemporaryUpgrade() && priority != task.getPriorityLevelNumber()) {
                    borrowed.incrementAndGet();
                }
                Date upgradeDeadline = task.getUpgradeDeadline();
                if (null == upgradeDeadline || !upgradeDeadline.after(new Date())) {
                    if (1 == task.getTemporaryUpgrade()) {
                        demoted.incrementAndGet();
                    }
                    task.setTemporaryUpgrade(0);
                }
                task.setUpdateTime(new Date());
                elongQueryPriceTaskMapper.updateAddCount(task);

                LocalDate checkIn = LocalDate.now().plusDays(task.getDelayCheckIn());
                LocalDate checkOut = LocalDate.now().plusDays(task.getDelayCheckOut());
                for (int adults : occupancyAdults) {
                    // 限流不在此处：queryPricesCache 这条路声明 CallPurpose.REFRESH，
                    // 通道层按用途扣「用途桶 + 接口桶」各一格并阻塞排队（每个占用各占一次调用）
                    // roomNum 恒 1 是已验证的选择，不是漏了一维：缓存键不含间数、缓存价也是
                    // 单间口径，多间在验价与下单侧乘间数（H001188）。2026-08-24 生产 A/B 实测
                    // hotel.detail 不按 NumberOfRooms 过滤可售集合与单间价（8 家 × 1/2/5 间交替
                    // 各 2 轮零差异，含 CurrentAlloment=1 的产品问 5 间照样返回）；艺龙【国际酒店】
                    // 国际对接指南（open.elong.com/faq/detail?plt=2&id=337）亦只要求 NumberOfAdults
                    // 与 ChildAges 与 detail 保持一致，未提间数。故按间数扩刷是纯额度浪费。
                    PriceReq request = PriceReq.builder().adultNum(adults).childNum(0).guestType(0)
                            .childAges(new ArrayList<>()).checkIn(checkIn.toString())
                            .checkout(checkOut.toString()).roomNum(1).build();
                    Supplier supplier = Supplier.builder()
                            .supplierId(SupplierSourceEnum.ELONG.getCode())
                            .sHotelId(task.getShId()).build();

                    List<ProductRespDTO> products = elongPriceService.queryPricesCache(request, supplier);
                    if (products == null) {
                        // 调用未取得结果：频控命中或网络/解析失败。
                        // 两者都不该动缓存（F-5.1），计入 failed 供调速参考
                        failed.incrementAndGet();
                    } else if (products.isEmpty()) {
                        empty.incrementAndGet();
                    } else {
                        onSale.incrementAndGet();
                    }
                    // 轮内进度：三态计数之和即已处理的调用数，逐次更新（gauge 只覆盖值，不累加）
                    Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, roundTags,
                            onSale.get() + empty.get() + failed.get());
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("elongQueryPriceTask 单行异常, shId={}", task.getShId(), e);
            }
            });
        }
        // 等它跑完，**不设截断**。刷价是后台循环、没有外部截止时间，跑久一点没有代价；
        // 而"跑太久"本来就有正确的处理，不需要在这里另造一套：
        //
        // ① 单行已被封死：HTTP 层连接/读取超时各 10s（HttpUtils.TIME_OUT）、重试次数有限，
        //    故一轮时长必然收敛——"一直等"不会卡死；
        // ② 一轮跑过 cron 周期已有处理：供应商执行器是单线程 + SynchronousQueue，下一次 cron
        //    会被拒掉并打"上一轮未结束，本轮跳过"。跳一次是安全的，一行都不丢。
        //
        // 此处原先自造了「等待预算 + shutdownNow」，凭空造出一条数据丢失路径。预算按
        // 行数/配额 算，隐含假设总能跑到配置的 QPS，而实际速率是 min(配额, 并发度/单行耗时)。
        // 2026-08-19 生产实证：常规档配额 4、并发 3，实际只有 1.82 QPS，预算 220s 与实需 220s
        // 撞上，连续三轮被截断、每轮丢 166/400 = 41% 的行；更糟的是那些行的 last_time 已在
        // 调用供应商之前更新，会被当成"刚刷过"排到队尾饿死，要等下一次全量才轮到。
        pool.shutdown();
        try {
            long expectedSec = Math.max(60, estimateRoundSeconds(list.size() * occupancyAdults.size(), qps, concurrency));
            while (!pool.awaitTermination(expectedSec, TimeUnit.SECONDS)) {
                // 超出预期只报告、不动手。把已处理数打出来，免得"共 N 行"被读成"N 行都处理了"
                int done = onSale.get() + empty.get() + failed.get();
                log.warn("elongQueryPriceTask 本轮超出预期仍在继续（不截断）,trigger={},priority={},"
                                + "已处理={}/{},预期={}s,并发={},qps={}"
                                + " —— 下一次调度会被「上一轮未结束」跳过，不丢行",
                        trigger, priority, done, list.size(), expectedSec, concurrency, qps);
            }
        } catch (InterruptedException e) {
            // 只有容器关闭才该打断在跑的行；此时如实报出已处理数
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            log.warn("elongQueryPriceTask 本轮被中断（容器关闭）,trigger={},priority={},已处理={}/{}",
                    trigger, priority, onSale.get() + empty.get() + failed.get(), list.size());
        }
        // §6.1.2：三态各自计数——"刷不出价"是频控还是真无房，处置完全不同
        long roundCost = System.currentTimeMillis() - roundStart;
        // §3.9.1：可数的业务指标不许只活在日志文本里——日志会被冲掉(生产实测 40 万行仅覆盖
        // 44 分钟)、发版即清空。这些数正是要跨天比的:失败率决定该不该调速、借入/复位的漂移
        // 是孤儿行的信号(issue #95)。日志行照留(§6.1.1 不许因为加了指标就删日志)。
        // 在轮次边界一次上报,不在循环体内逐条打——逐条既放大写入量,也拿不到"这一轮"的口径。
        Monitor.recordMany(MetricNames.REFRESH_ROWS, roundTags, totalCalls);
        Monitor.recordMany(MetricNames.REFRESH_ONSALE, roundTags, onSale.get());
        Monitor.recordMany(MetricNames.REFRESH_EMPTY, roundTags, empty.get());
        Monitor.recordMany(MetricNames.REFRESH_FAILED, roundTags, failed.get());
        Monitor.recordMany(MetricNames.REFRESH_BORROWED, roundTags, borrowed.get());
        Monitor.recordMany(MetricNames.REFRESH_DEMOTED, roundTags, demoted.get());
        Monitor.recordTime(MetricNames.REFRESH_ROUND, roundTags, roundCost);
        // 轮次收尾把进度归零：留着上一轮的终值会让面板在两轮之间显示「已处理=满」，
        // 看着像一直在跑。归零后「done 从 0 爬到 total」就是一轮的形状
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, roundTags, 0);
        log.info("elongQueryPriceTask 本轮结束, trigger={}, priority={}, 共 {} 行, 有在售={}, 无在售={}, 失败={}, 借入={}, 复位={}, 并发={}, 实际 {} QPS, 耗时 {} ms",
                trigger, priority, list.size(), onSale.get(), empty.get(), failed.get(),
                borrowed.get(), demoted.get(), concurrency,
                roundCost > 0 ? String.format("%.2f", list.size() * 1000.0 / roundCost) : "-", roundCost);
        return true;
    }

    /**
     * 单行耗时，用于估算<b>吞吐</b>。取生产实测均值 1.45s，向上取 1.5s。
     *
     * <p>不能取 p90(3.13s)：p90 管的是"单独一行最坏要多久"，而并发下的整轮吞吐由<b>均值</b>
     * 决定。用 p90 算会高估近一倍——并发 3 实测跑出 1.82 QPS(反推单行 1.65s)，按 p90 算却得
     * 0.94 QPS。这个估值现在只用来定"多久没跑完该报告一声"的阈值，宁可略早报，不要虚高。
     */
    private static final double PER_ROW_SEC_MEAN = 1.5;

    /**
     * 本轮<b>预计</b>耗时(秒)。只用来决定"多久还没跑完就该报告一声"，<b>不用来截断</b>。
     *
     * <p>关键是取 {@code min(配额, 并发能力)} 的下限。只按配额算会隐含假设"总能跑到配置的
     * QPS"，而并发度配得低时跑不到——2026-08-19 生产实证：常规档配额 4 QPS、并发 3，实际
     * 只有 1.82 QPS。曾经拿这个偏小的估值当截断预算，导致每轮丢 41% 的行（见上方等待段注释）。
     *
     * @param rows        本轮行数
     * @param qps         限流子配额
     * @param concurrency 行级并发度
     */
    static long estimateRoundSeconds(int rows, double qps, int concurrency) {
        double capacityQps = Math.max(concurrency, 1) / PER_ROW_SEC_MEAN;
        double effectiveQps = Math.min(Math.max(qps, 0.1), capacityQps);
        return (long) Math.ceil(rows / effectiveQps);
    }
}
