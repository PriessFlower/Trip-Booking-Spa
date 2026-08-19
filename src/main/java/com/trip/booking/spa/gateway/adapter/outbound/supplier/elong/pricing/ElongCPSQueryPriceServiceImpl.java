package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 艺龙刷价：消费 {@code elong_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>结构照搬 {@code ExpediaCPSQueryPriceServiceImpl}（一轮一返回、Redisson 锁互斥、
 * 单行 try 隔离坏数据），差异全部来自艺龙的两条硬约束：
 *
 * <ul>
 *   <li><b>额度与 tg-trip-cursor 共享</b>：艺龙 10 QPS 是账号级硬额度，cursor 的刷价
 *       同时在用。2026-08-17 生产实测：0.62 QPS 时查价成功率 73%，降到约 0.4 QPS 后
 *       92%，失败几乎全是 A201010001（访问太频繁）。故默认 QPS 取 0.3 的保守值，
 *       且失败分类计数——频控与真实业务错误必须分开看，否则调速没有依据</li>
 *   <li><b>逐店查询</b>：一行任务 = 一次 hotel.detail 调用，不批量聚合（移植风险⑤）</li>
 * </ul>
 *
 * <p><b>提速的三条上限</b>（2026-08-19 实测后并发化，缺一条都会踩坑）：
 * <ol>
 *   <li><b>供应商配额</b>：查价在统一限流里是 5 QPS（{@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES}），
 *       但这 5 个额度<b>刷价与验价共用</b>——验价的"现取现验"每次先重打一次查价。刷价吃干会让
 *       客人正在点的验价卡在 acquire 超时上失败，故本轮的 qps 须<b>低于</b>那 5，留头给验价；</li>
 *   <li><b>连接池</b>：并发行会同时占 Redis 与 DB 连接，而<b>出价链路也在读同一个 Redis</b>。
 *       并发度必须留在池容量之内，否则提速的代价是拖慢真实客流；</li>
 *   <li><b>并发度 ≈ 目标 QPS × 单行耗时</b>。给太小则限流器空转、跑不到目标；给太大则线程堆在
 *       {@code acquire()} 上等许可，超过 {@code ratelimit.acquire-timeout-ms} 就变成假失败。</li>
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
     * 刷价在查价配额里的<b>子配额</b>键，配在 Nacos 的 {@code ratelimit.qps}（§3.3：限流一律走统一限流）。
     *
     * <p>为什么需要子配额而不是直接用接口总额：{@code SPA_SUPPLIER_API_PRODUCT_PRICES} 那 5 QPS
     * 是刷价与<b>验价</b>共用的——验价"现取现验"每次先重打一次查价。刷价并发化后会持续贴着上限跑，
     * 若与验价共用同一个键，客人正在点的验价会被刷价挤到 acquire 超时。故给刷价单独一档、留头给验价。
     *
     * <p>键名沿用 {@code GLOBAL_LIMIT:<供应商>:<接口>} 再加用途后缀，使它在配置里紧邻父配额，
     * 一眼能看出"子配额之和不得超过父配额"。
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
        int batchSize = priority == 0
                ? environment.getProperty("task.elong-cps.high-batch-size", Integer.class, 400)
                : environment.getProperty("task.elong-cps.normal-batch-size", Integer.class, 200);
        // 行级并发度。串行时每行 1.45s 均值(p50 1.08 / p90 3.13 / p99 4.51,2026-08-19 生产实测),
        // 于是实际只跑出 0.89 QPS——连当时设的 2 QPS 限额都摸不到,限流器根本不是瓶颈。
        // 要真跑到目标 QPS,并发度须 ≈ QPS × 单行耗时;取值别超连接池(见类注释"三条上限")。
        // 兜底取 1(串行)是安全侧(§3.3.3):并发化是新引入的能力,Nacos 读不到时应退回
        // <b>已知安全的旧行为</b>,而不是静默采用新行为。串行只是慢,而并发在连接池配置同时
        // 未生效的情况下会与出价抢连接——出价是真实客流。生产运行值(高频 6 / 常规 3)在 Nacos。
        int concurrency = priority == 0
                ? environment.getProperty("task.elong-cps.high-concurrency", Integer.class, 1)
                : environment.getProperty("task.elong-cps.normal-concurrency", Integer.class, 1);
        // 护栏：子配额未登记时 qpsOf 会回落 default-qps（生产 20），刷价会瞬间把账号额度烧穿。
        // 漂移 CI 只核对配置键路径，管不到 qps 这张 JSON 表里的条目，故在运行期显式喊出来。
        double interfaceTotal = rateLimitProperties.qpsOf("GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES");
        if (qps >= interfaceTotal) {
            log.error("[gate] 刷价子配额 {} 未登记或不小于接口总额({}), 生效值={} —— 按总额的一半保守下调，"
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceTotal, qps);
            qps = interfaceTotal / 2;
        }
        List<ElongQueryPriceTask> list = elongQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("elongQueryPriceTask 本轮开始, trigger={}, priority={}, 取到 {} 行, batchSize={}, qps={}, 并发={}",
                trigger, priority, list.size(), batchSize, qps, concurrency);
        if (CollectionUtils.isEmpty(list)) {
            log.info("elongQueryPriceTask 本轮结束, trigger={}, 无待刷任务, 耗时 {} ms",
                    trigger, System.currentTimeMillis() - roundStart);
            return true;
        }

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
                RateLimitHolder.get().acquire(REFRESH_LIMIT_KEY);

                PriceReq request = PriceReq.builder().adultNum(1).childNum(0).guestType(0)
                        .childAges(new ArrayList<>()).checkIn(checkIn.toString())
                        .checkout(checkOut.toString()).roomNum(1).build();
                Supplier supplier = Supplier.builder()
                        .supplierId(SupplierSourceEnum.ELONG.getCode())
                        .sHotelId(task.getShId()).build();

                List<ProductRespDTO> products = elongPriceService.queryPricesCache(request, supplier);
                if (products == null) {
                    // 调用未取得结果：绝大多数是账号级频控（与 cursor 抢额度），
                    // 少数是网络/解析。两者都不该动缓存，计入 failed 供调速参考
                    failed.incrementAndGet();
                } else if (products.isEmpty()) {
                    empty.incrementAndGet();
                } else {
                    onSale.incrementAndGet();
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("elongQueryPriceTask 单行异常, shId={}", task.getShId(), e);
            }
            });
        }
        // 必须等本轮所有行落地才收口:计数与日志否则会少算,而"本轮结束"那行日志是
        // 唯一的轮次事实来源。超时上限按最坏情况给足(每行 p99 4.5s,并发后仍留富余),
        // 到点未完则强制收尾并记日志——宁可暴露超时,不可静默截断。
        pool.shutdown();
        try {
            long budgetSec = Math.max(60, (long) Math.ceil(list.size() / Math.max(qps, 0.1)) + 120);
            if (!pool.awaitTermination(budgetSec, TimeUnit.SECONDS)) {
                log.warn("elongQueryPriceTask 本轮超时未完成,强制收尾, trigger={}, priority={}, 预算 {}s",
                        trigger, priority, budgetSec);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            log.warn("elongQueryPriceTask 本轮被中断, trigger={}, priority={}", trigger, priority);
        }
        // §6.1.2：三态各自计数——"刷不出价"是频控还是真无房，处置完全不同
        long roundCost = System.currentTimeMillis() - roundStart;
        // §3.9.1：可数的业务指标不许只活在日志文本里——日志会被冲掉(生产实测 40 万行仅覆盖
        // 44 分钟)、发版即清空。这些数正是要跨天比的:失败率决定该不该调速、借入/复位的漂移
        // 是孤儿行的信号(issue #95)。日志行照留(§6.1.1 不许因为加了指标就删日志)。
        // 在轮次边界一次上报,不在循环体内逐条打——逐条既放大写入量,也拿不到"这一轮"的口径。
        Map<String, Object> tags = new HashMap<>(2);
        tags.put("supplier", "elong");
        tags.put("priority", String.valueOf(priority));
        Monitor.recordMany("refresh_rows", tags, list.size());
        Monitor.recordMany("refresh_onsale", tags, onSale.get());
        Monitor.recordMany("refresh_empty", tags, empty.get());
        Monitor.recordMany("refresh_failed", tags, failed.get());
        Monitor.recordMany("refresh_borrowed", tags, borrowed.get());
        Monitor.recordMany("refresh_demoted", tags, demoted.get());
        Monitor.recordTime("refresh_round", tags, roundCost);
        log.info("elongQueryPriceTask 本轮结束, trigger={}, priority={}, 共 {} 行, 有在售={}, 无在售={}, 失败={}, 借入={}, 复位={}, 并发={}, 实际 {} QPS, 耗时 {} ms",
                trigger, priority, list.size(), onSale.get(), empty.get(), failed.get(),
                borrowed.get(), demoted.get(), concurrency,
                roundCost > 0 ? String.format("%.2f", list.size() * 1000.0 / roundCost) : "-", roundCost);
        return true;
    }
}
