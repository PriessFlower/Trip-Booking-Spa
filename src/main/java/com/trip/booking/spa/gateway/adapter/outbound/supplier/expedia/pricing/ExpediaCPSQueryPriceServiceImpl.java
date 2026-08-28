package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
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
 * <p><b>速率已收口到统一限流</b>：声明 {@link CallPurpose#REFRESH}，通道层扣用途桶与接口桶各
 * 一格。此前是自建 Guava 限流器 + {@code task.expedia-cps.qps}，与统一限流并存。
 * <b>发布时必须同时在 Nacos 补 {@code EXPEDIA:...:PRODUCT_PRICES:REFRESH}</b>，否则未登记会回落
 * 接口桶（50）——那比原来的 10 快五倍。
 */
@Slf4j
@Service
public class ExpediaCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<ExpediaQueryPriceTask>
        implements ExpediaCPSQueryPriceService {

    /** 刷价互斥锁：定时调度与 BackDoor 手动触发共用，保证同一时刻仅一个执行者（§3.8.2） */
    private static final String LOCK_KEY = "task:lock:expediaCpsQueryPrice";

    /** 刷价的用途桶键，与艺龙同构 */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:EXPEDIA:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    @Autowired
    private Environment environment;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ExpediaQueryPriceTaskMapper expediaQueryPriceTaskMapper;

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Autowired
    private com.trip.booking.spa.platform.ratelimit.RateLimitProperties rateLimitProperties;

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
        return List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2);
    }

    @Override
    protected int batchSize(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.expedia-cps.slow-batch-size", Integer.class, 100);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.expedia-cps.mid-batch-size", Integer.class, 200);
            case 2 -> environment.getProperty("task.expedia-cps.far-batch-size", Integer.class, 200);
            default -> environment.getProperty("task.expedia-cps.batch-size", Integer.class, 200);
        };
    }

    /**
     * 兜底 1（串行）＝改动前的行为。Expedia 此前完全串行，本次搬骨架不顺带并发化——
     * 并发度要与连接池一起评估，而出价链路共用同一个池（§3.3.3 兜底取已知安全的旧行为）。
     */
    @Override
    protected int concurrency(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.expedia-cps.slow-concurrency", Integer.class, 1);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.expedia-cps.mid-concurrency", Integer.class, 1);
            case 2 -> environment.getProperty("task.expedia-cps.far-concurrency", Integer.class, 1);
            default -> environment.getProperty("task.expedia-cps.concurrency", Integer.class, 1);
        };
    }

    /**
     * 只用于轮次日志与耗时预估；真正的扣格在通道层（用途桶 + 接口桶）。
     *
     * <p>2026-08-25 收口：此前 Expedia 刷价自建 Guava 限流器、速率配在 {@code task.expedia-cps.qps}，
     * 与统一限流并存——同一件事两个开关，运维改了统一限流却不生效（§3.3）。现与艺龙同形。
     */
    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:EXPEDIA:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<ExpediaQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return expediaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /**
     * 每行按这些占用各查一次。占用既是 productKey 的成分，也是缓存键的一维——<b>没刷的占用，
     * 该占用的查询就如实拿空</b>。艺龙有同构事故可循：只刷 1 人而高德按 2 人问价，曝光刷新
     * 断粮 16h 后被 RATE_DEAD 整体撤下（2026-08-22，见 {@code ElongCPSQueryPriceServiceImpl}
     * 同名方法注释）。
     *
     * <p>该刷哪些占用取决于渠道实际按几人问价，属运营口径，故取 Nacos 运行时键、可随时调；
     * 兜底 "2" 是改为配置项之前的写死值，保证键缺席时行为不变。
     */
    @Override
    protected List<String> dimensions() {
        return java.util.Arrays.stream(
                        environment.getProperty("task.expedia-cps.occupancies", "2").split(","))
                .map(String::trim).filter(v -> !v.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(ExpediaQueryPriceTask row, String dimension) {
        // Expedia 是美国供应商，日期按 JVM 默认时区计——这是改动前的行为，未经核实故保留。
        // 艺龙那侧已显式指定 Asia/Shanghai（供应商口径），Expedia 的正确口径待与对接方确认
        LocalDate today = LocalDate.now();
        PriceReq request = PriceReq.builder()
                .adultNum(Integer.parseInt(dimension)).childNum(0)
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
     * 刷完调档：无货沉入本档无货位（+10），刷出有货回原档（-10）；失败不动档
     * （F-5.1：一次网络抖动不该把店打进慢车道）。算法与艺龙/飞猪共用
     * {@link #soldOutOffsetTarget}，此前 Expedia 未实现此钩子——满房的店不会沉入
     * 慢车道，一直按同频消耗额度。
     */
    @Override
    protected void adjustPriority(ExpediaQueryPriceTask row, RefreshOutcome outcome) {
        int target = soldOutOffsetTarget(row.getPriorityLevelNumber(), outcome);
        if (target != row.getPriorityLevelNumber()) {
            expediaQueryPriceTaskMapper.updatePriority(row.getId(), target);
        }
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
