package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.MeituanQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanQueryPriceTask;
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
 * 美团刷价：消费 {@code meituan_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>调度骨架（锁、连续消费、取批、并发、三态计数、指标、不截断等待）在
 * {@link AbstractCPSQueryPriceService}，本类只回答本家专有的四件事：取哪一批、按哪些占用刷、
 * 怎么刷一行、刷完怎么记账。
 *
 * <p><b>一行 = 一次调用，尽管接口支持合批</b>：{@code hotelIds} 是列表，可以一次问多家；
 * 但 2026-09-14 实测本家可卖清单只有 847 家，逐店刷跑得起，没必要为省配额而放弃
 * "哪一行刷失败了"这个可归因性。清单显著变大时再议合批。
 *
 * <p><b>没有令牌</b>：本家每请求现签（HMAC-SHA1），无会话无续期，故这条路上只有查价一种
 * 出网调用，不像差旅无忧那样要操心续期的用途。
 *
 * <p><b>速率不在本类</b>：通道层据 {@link CallPurpose} 扣用途桶与接口桶各一格并阻塞排队。
 * 取值只在 Nacos 的 {@code ratelimit.qps}。
 */
@Slf4j
@Service
public class MeituanCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<MeituanQueryPriceTask>
        implements MeituanCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:meituan:cps:query-price";

    /** 刷价的用途桶键。本类不手写 acquire——用途由 CallPurpose 声明、通道层扣格；这里只读它打日志 */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:MEITUAN:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    /**
     * 入住日按<b>北京时间</b>算。任务行存的是"相对今天的偏移"，那个"今天"必须有唯一口径，
     * 不能跟着容器时区走——生产容器现在是 Asia/Shanghai，但那是一个随时可被改动的环境变量，
     * 判据不该挂在它上面。取客源地时区，与另五家一致。
     */
    private static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private MeituanQueryPriceTaskMapper meituanQueryPriceTaskMapper;

    @Resource
    private MeituanPriceService meituanPriceService;

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
        return SupplierSourceEnum.MEITUAN;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.meituan-cps.enabled", Boolean.class, false);
    }

    /** 档位=住期远近（与另五家统一）：0=T+0~2 / 1=T+3~7 / 2=T+8~30，无货态=N+10（模板偏移算法） */
    @Override
    protected List<Integer> tiers() {
        return List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2);
    }

    @Override
    protected int batchSize(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.meituan-cps.slow-batch-size", Integer.class, 100);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.meituan-cps.mid-batch-size", Integer.class, 200);
            case 2 -> environment.getProperty("task.meituan-cps.far-batch-size", Integer.class, 200);
            default -> environment.getProperty("task.meituan-cps.batch-size", Integer.class, 200);
        };
    }

    /**
     * 行级并发度。兜底取 1（串行）是安全侧（§3.3.3）：并发在连接池未同步放大时会与出价抢连接，
     * 而出价是真实客流。
     */
    @Override
    protected int concurrency(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.meituan-cps.slow-concurrency", Integer.class, 1);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.meituan-cps.mid-concurrency", Integer.class, 1);
            case 2 -> environment.getProperty("task.meituan-cps.far-concurrency", Integer.class, 1);
            default -> environment.getProperty("task.meituan-cps.concurrency", Integer.class, 1);
        };
    }

    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:MEITUAN:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<MeituanQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return meituanQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /**
     * 每行按这些成人数各查一次。本家的 {@code numberOfAdults} 是<b>每间房</b>人数且进请求，
     * 缺哪一档就等于那一档的查询恒空——高德按 2 人问价，故默认 2 人。取值为 Nacos 运行时键。
     */
    @Override
    protected List<String> dimensions() {
        return Arrays.stream(environment.getProperty("task.meituan-cps.occupancies", "2").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(MeituanQueryPriceTask row, String dimension) {
        // 组装请求、写缓存、三态映射全在骨架（refreshViaQuery）
        return refreshViaQuery(row, dimension);
    }

    /** 只查不写：写缓存与三态由骨架统一做 */
    @Override
    protected PricingResult queryForRefresh(PriceQuery request) {
        return meituanPriceService.queryPrices(request, CallPurpose.REFRESH);
    }

    @Override
    protected ZoneId supplierZone() {
        return SUPPLIER_ZONE;
    }

    @Override
    protected void markRefreshed(MeituanQueryPriceTask row) {
        row.setUpdateTime(new Date());
        meituanQueryPriceTaskMapper.updateAddCount(row);
    }

    @Override
    protected void adjustPriority(MeituanQueryPriceTask row, RefreshOutcome outcome) {
        int target = soldOutOffsetTarget(row.getPriorityLevelNumber(), outcome);
        if (target != row.getPriorityLevelNumber()) {
            meituanQueryPriceTaskMapper.updatePriority(row.getId(), target);
        }
    }
}
