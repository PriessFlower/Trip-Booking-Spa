package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.DidaQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 道旅刷价：消费 {@code dida_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>调度骨架（锁、连续消费、取批、并发、三态计数、指标、不截断等待）在
 * {@link AbstractCPSQueryPriceService}，本类只回答道旅专有的四件事：取哪一批、按哪些占用刷、
 * 怎么刷一行、刷完怎么记账。
 *
 * <p><b>逐店查询</b>：一行任务 = 一次 pricesearch。多店 + 实时报价会被道旅降级
 * （2026-09-08 实测：逐店实时 280 条 vs 10 家一批实时 105 条、4/10 家整家消失），
 * 故不做批量聚合。
 *
 * <p><b>若将来为省调用数要合批，必须同时把 {@code IsRealTime} 关掉，而那一档有个硬代价</b>：
 * 官方 price-search 注 2「默认情况下，Lowest price search 和 Cache rate search 将返回基于
 * 2 人的价格」，2026-09-08 实测坐实——缓存档<b>完全无视请求里的占用</b>，问 1 人、3 人、
 * 2 大 1 小，回来的都是同一份 2 人报价（响应里的 {@code RoomOccupancy} 恒为 2 成人 0 儿童），
 * 而实时档按占用真回（同一家酒店 1 人 534 / 2 人 610 / 3 人 801 / 2 大 1 小 758）。
 * 即：合批+缓存只在「只刷 2 人档」这一个前提下与逐店实时等值；{@code occupancies} 一旦
 * 配了 2 以外的值，缓存档会把 2 人价当成那一档的价刷进缓存。
 *
 * <p><b>速率不在本类</b>：刷价这条路声明 {@link CallPurpose#REFRESH}，通道层据此扣用途桶与
 * 接口桶各一格并阻塞排队。取值只在 Nacos 的 {@code ratelimit.qps}。
 */
@Slf4j
@Service
public class DidaCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<DidaQueryPriceTask>
        implements DidaCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:dida:cps:query-price";

    /** 刷价的用途桶键。本类不手写 acquire——用途由 CallPurpose 声明、通道层扣格；这里只读它打日志 */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    /**
     * 入住日按<b>北京时间</b>算。道旅卖的是全球库存、日期是酒店当地日，但我方任务行存的是
     * "相对今天的偏移"，那个"今天"必须有唯一口径，否则容器时区一变（生产容器跑 UTC）
     * 北京 00:00~08:00 这八小时的 T+0 会算成昨天。取客源地时区与另两家一致。
     */
    private static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private DidaQueryPriceTaskMapper didaQueryPriceTaskMapper;

    @Resource
    private DidaPriceService didaPriceService;

    @Resource
    private Environment environment;

    @Resource
    private RateLimitProperties rateLimitProperties;

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
        return SupplierSourceEnum.DIDA;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.dida-cps.enabled", Boolean.class, false);
    }

    /** 档位=住期远近（与另两家统一）：0=T+0~2 / 1=T+3~7 / 2=T+8~30，无货态=N+10（模板偏移算法） */
    @Override
    protected List<Integer> tiers() {
        return List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2);
    }

    @Override
    protected int batchSize(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.dida-cps.slow-batch-size", Integer.class, 100);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.dida-cps.mid-batch-size", Integer.class, 200);
            case 2 -> environment.getProperty("task.dida-cps.far-batch-size", Integer.class, 200);
            default -> environment.getProperty("task.dida-cps.batch-size", Integer.class, 200);
        };
    }

    /**
     * 行级并发度。兜底取 1（串行）是安全侧（§3.3.3）：并发在连接池未同步放大时会与出价抢连接，
     * 而出价是真实客流。
     */
    @Override
    protected int concurrency(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.dida-cps.slow-concurrency", Integer.class, 1);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.dida-cps.mid-concurrency", Integer.class, 1);
            case 2 -> environment.getProperty("task.dida-cps.far-concurrency", Integer.class, 1);
            default -> environment.getProperty("task.dida-cps.concurrency", Integer.class, 1);
        };
    }

    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<DidaQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return didaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /**
     * 每行按这些成人数各查一次。道旅报价按占用签发（RealTimeOccupancy 进请求），
     * 缺哪一档就等于那一档的查询恒空——高德按 2 人问价，故默认 2 人。取值为 Nacos 运行时键。
     */
    @Override
    protected List<String> dimensions() {
        return Arrays.stream(environment.getProperty("task.dida-cps.occupancies", "2").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(DidaQueryPriceTask row, String dimension) {
        // 组装请求、写缓存、三态映射全在骨架（refreshViaQuery）
        return refreshViaQuery(row, dimension);
    }

    /** 只查不写：写缓存与三态由骨架统一做 */
    @Override
    protected PricingResult queryForRefresh(PriceReq request, Supplier supplier) {
        return didaPriceService.queryPrices(request, supplier, CallPurpose.REFRESH);
    }

    @Override
    protected ZoneId supplierZone() {
        return SUPPLIER_ZONE;
    }

    @Override
    protected void markRefreshed(DidaQueryPriceTask row) {
        row.setUpdateTime(new Date());
        didaQueryPriceTaskMapper.updateAddCount(row);
    }

    @Override
    protected void adjustPriority(DidaQueryPriceTask row, RefreshOutcome outcome) {
        int target = soldOutOffsetTarget(row.getPriorityLevelNumber(), outcome);
        if (target != row.getPriorityLevelNumber()) {
            didaQueryPriceTaskMapper.updatePriority(row.getId(), target);
        }
    }
}
