package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ClwyQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
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
 * 差旅无忧刷价：消费 {@code clwy_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>调度骨架（锁、连续消费、取批、并发、三态计数、指标、不截断等待）在
 * {@link AbstractCPSQueryPriceService}，本类只回答本家专有的四件事：取哪一批、按哪些占用刷、
 * 怎么刷一行、刷完怎么记账。
 *
 * <p><b>一行 = 一次报价档调用</b>：本家的 GetPrice 不传 RatePlanId 时是整店口径，一次带回该店
 * 该住期的全部房型与价格计划，故批的单位天然就是店，没有合批这个选项。
 *
 * <p><b>令牌续期寄生在业务调用里</b>：刷价这条路的用途是 {@link CallPurpose#REFRESH}，
 * 续期也会按 REFRESH 去扣令牌桶（用途由调用方透传，见 {@code ClwyTokenProvider#token}）。
 * 若写死成前台口径，续期那一下会快速失败，连带那一行刷价白白失败一次。
 *
 * <p><b>速率不在本类</b>：通道层据 {@link CallPurpose} 扣用途桶与接口桶各一格并阻塞排队。
 * 取值只在 Nacos 的 {@code ratelimit.qps}。
 */
@Slf4j
@Service
public class ClwyCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<ClwyQueryPriceTask>
        implements ClwyCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:clwy:cps:query-price";

    /** 刷价的用途桶键。本类不手写 acquire——用途由 CallPurpose 声明、通道层扣格；这里只读它打日志 */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:CLWY:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    /**
     * 入住日按<b>北京时间</b>算。任务行存的是"相对今天的偏移"，那个"今天"必须有唯一口径，
     * 不能跟着容器时区走——生产容器现在是 Asia/Shanghai，但那是一个随时可被改动的环境变量，
     * 判据不该挂在它上面。取客源地时区，与另四家一致。
     */
    private static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ClwyQueryPriceTaskMapper clwyQueryPriceTaskMapper;

    @Resource
    private ClwyPriceService clwyPriceService;

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
        return SupplierSourceEnum.CLWY;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.clwy-cps.enabled", Boolean.class, false);
    }

    /** 档位=住期远近（与另四家统一）：0=T+0~2 / 1=T+3~7 / 2=T+8~30，无货态=N+10（模板偏移算法） */
    @Override
    protected List<Integer> tiers() {
        return List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2);
    }

    @Override
    protected int batchSize(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.clwy-cps.slow-batch-size", Integer.class, 100);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.clwy-cps.mid-batch-size", Integer.class, 200);
            case 2 -> environment.getProperty("task.clwy-cps.far-batch-size", Integer.class, 200);
            default -> environment.getProperty("task.clwy-cps.batch-size", Integer.class, 200);
        };
    }

    /**
     * 行级并发度。兜底取 1（串行）是安全侧（§3.3.3）：并发在连接池未同步放大时会与出价抢连接，
     * 而出价是真实客流。
     */
    @Override
    protected int concurrency(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.clwy-cps.slow-concurrency", Integer.class, 1);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.clwy-cps.mid-concurrency", Integer.class, 1);
            case 2 -> environment.getProperty("task.clwy-cps.far-concurrency", Integer.class, 1);
            default -> environment.getProperty("task.clwy-cps.concurrency", Integer.class, 1);
        };
    }

    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:CLWY:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<ClwyQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return clwyQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /**
     * 每行按这些成人数各查一次。本家的 {@code AdultCount} 是<b>每间房</b>人数且进请求，
     * 缺哪一档就等于那一档的查询恒空——高德按 2 人问价，故默认 2 人。取值为 Nacos 运行时键。
     */
    @Override
    protected List<String> dimensions() {
        return Arrays.stream(environment.getProperty("task.clwy-cps.occupancies", "2").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(ClwyQueryPriceTask row, String dimension) {
        // 组装请求、写缓存、三态映射全在骨架（refreshViaQuery）
        return refreshViaQuery(row, dimension);
    }

    /** 只查不写：写缓存与三态由骨架统一做 */
    @Override
    protected PricingResult queryForRefresh(PriceQuery request) {
        return clwyPriceService.queryPrices(request, CallPurpose.REFRESH);
    }

    @Override
    protected ZoneId supplierZone() {
        return SUPPLIER_ZONE;
    }

    @Override
    protected void markRefreshed(ClwyQueryPriceTask row) {
        row.setUpdateTime(new Date());
        clwyQueryPriceTaskMapper.updateAddCount(row);
    }

    @Override
    protected void adjustPriority(ClwyQueryPriceTask row, RefreshOutcome outcome) {
        int target = soldOutOffsetTarget(row.getPriorityLevelNumber(), outcome);
        if (target != row.getPriorityLevelNumber()) {
            clwyQueryPriceTaskMapper.updatePriority(row.getId(), target);
        }
    }
}
