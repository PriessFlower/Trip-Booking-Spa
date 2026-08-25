package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Expedia 刷价：消费 {@code expedia_query_price_task}，把查回的价格写入我方 Redis 缓存。
 *
 * <p>调度骨架在 {@link AbstractCPSQueryPriceService}；本类只回答 Expedia 专有的四件事。
 * 搬上骨架后顺带得到此前完全缺失的东西：三态计数、{@code refresh_*} 指标（O-4.3 要求两家同名
 * 指标、supplier 作标签，此前艺龙有七个指标而 Expedia 零埋点，两家成功率无法同图对比）、
 * 行级并发、轮内进度、连续消费。
 *
 * <p><b>速率仍是自建 Guava 限流器</b>（{@code task.expedia-cps.qps}），与艺龙的"统一限流用途桶"
 * 不同——这是待收口的欠账（§3.3 限流一律走统一限流）。收口要同时在 Nacos 补
 * {@code GLOBAL_LIMIT:EXPEDIA:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH}，否则未登记时会回落到
 * 接口桶 50 QPS——比现在的 10 快五倍，属于不该顺带发生的行为变更。故本次只搬骨架，速率不动。
 */
@Slf4j
@Service
public class ExpediaCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<ExpediaQueryPriceTask>
        implements ExpediaCPSQueryPriceService {

    /** 刷价互斥锁：定时调度与 BackDoor 手动触发共用，保证同一时刻仅一个执行者（§3.8.2） */
    private static final String LOCK_KEY = "task:lock:expediaCpsQueryPrice";

    @Autowired
    private Environment environment;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ExpediaQueryPriceTaskMapper expediaQueryPriceTaskMapper;

    @Autowired
    private ExpediaPriceService expediaPriceService;

    /**
     * 自建限流器（欠账，见类注释）。放在字段上而不是每轮新建：原实现每轮 {@code RateLimiter.create}，
     * 于是每轮的令牌桶都是空的、首个许可要等满一个周期，且跨轮不平滑。
     */
    private volatile RateLimiter rateLimiter;
    private volatile double rateLimiterQps;

    @Override
    public Boolean queryPriceQueueTask(String trigger) {
        return runUntilIdleOrClosed(trigger);
    }

    @Override
    protected RedissonClient redissonClient() {
        return redissonClient;
    }

    @Override
    protected String lockKey() {
        return LOCK_KEY;
    }

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.EXPEDIA;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.expedia-cps.enabled", Boolean.class, false);
    }

    /** Expedia 目前单档 */
    @Override
    protected List<Integer> tiers() {
        return List.of(0);
    }

    @Override
    protected int batchSize(int priority) {
        return environment.getProperty("task.expedia-cps.batch-size", Integer.class, 200);
    }

    /**
     * 兜底 1（串行）＝改动前的行为。Expedia 此前完全串行，本次搬骨架不顺带并发化——
     * 并发度要与连接池一起评估，而出价链路共用同一个池（§3.3.3 兜底取已知安全的旧行为）。
     */
    @Override
    protected int concurrency(int priority) {
        return environment.getProperty("task.expedia-cps.concurrency", Integer.class, 1);
    }

    @Override
    protected double declaredQps(int priority) {
        return environment.getProperty("task.expedia-cps.qps", Double.class, 0.5);
    }

    @Override
    protected List<ExpediaQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return expediaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /** Expedia 此前按 2 人写死一条，故只有一个维度。改成配置项属行为变更，本次不动 */
    @Override
    protected List<String> dimensions() {
        return List.of("2");
    }

    @Override
    protected RefreshOutcome refreshOne(ExpediaQueryPriceTask row, String dimension) {
        limiter(declaredQps(0)).acquire();
        // Expedia 是美国供应商，日期按 JVM 默认时区计——这是改动前的行为，未经核实故保留。
        // 艺龙那侧已显式指定 Asia/Shanghai（供应商口径），Expedia 的正确口径待与对接方确认
        LocalDate today = LocalDate.now();
        PriceReq request = PriceReq.builder()
                .adultNum(Integer.parseInt(dimension)).childNum(0).guestType(0)
                .childAges(new ArrayList<>())
                .checkIn(today.plusDays(row.getDelayCheckIn()).toString())
                .checkout(today.plusDays(row.getDelayCheckOut()).toString())
                .roomNum(1).build();
        Supplier supplier = Supplier.builder().sHotelId(row.getShId()).build();

        List<ProductRespDTO> products = expediaPriceService.queryPricesCache(request, supplier);
        if (products == null) {
            return RefreshOutcome.FAILED;
        }
        return products.isEmpty() ? RefreshOutcome.EMPTY : RefreshOutcome.ON_SALE;
    }

    /**
     * 记账。Expedia 多一步：跨天则把查询次数重置为 1——{@code query_count} 的语义是"今天刷了几次"，
     * 不重置会累加成历史总次数。
     */
    @Override
    protected void markRefreshed(ExpediaQueryPriceTask row) {
        if (!isSameDay(row.getUpdateTime(), row.getLastTime())) {
            row.setQueryCount(1);
        }
        expediaQueryPriceTaskMapper.updateAddCount(row);
    }

    /** 速率变了就换桶，否则复用——避免每轮重建导致的冷启动等待 */
    private RateLimiter limiter(double qps) {
        RateLimiter current = rateLimiter;
        if (current == null || rateLimiterQps != qps) {
            synchronized (this) {
                if (rateLimiter == null || rateLimiterQps != qps) {
                    rateLimiter = RateLimiter.create(qps);
                    rateLimiterQps = qps;
                }
                current = rateLimiter;
            }
        }
        return current;
    }

    public static boolean isSameDay(Date updateTime, Date lastTime) {
        // 新任务行 last_time 为空（从未刷过价），视为非同一天，走首次/新一天的计数重置
        if (null == updateTime || null == lastTime) {
            return false;
        }
        LocalDate a = updateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate b = lastTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return a.isEqual(b);
    }
}
