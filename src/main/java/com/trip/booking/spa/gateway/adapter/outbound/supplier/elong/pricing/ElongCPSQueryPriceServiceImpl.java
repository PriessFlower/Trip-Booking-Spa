package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 艺龙刷价：消费 {@code elong_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>调度骨架（锁、连续消费、取批、并发、三态计数、指标、不截断等待）在
 * {@link AbstractCPSQueryPriceService}，本类只回答艺龙专有的四件事：取哪一批、按哪些占用刷、
 * 怎么刷一行、刷完怎么记账。
 *
 * <p>艺龙的两条硬约束：
 * <ul>
 *   <li><b>逐店查询</b>：一行任务 = 一次 {@code hotel.detail}，不批量聚合（移植风险⑤）</li>
 *   <li><b>失败分类计数</b>：频控命中与真实业务错误必须分开看，否则调速没有依据（F-8.2）</li>
 * </ul>
 *
 * <p><b>速率不在本类</b>：刷价这条路声明 {@link CallPurpose#REFRESH}，通道层据此扣用途桶与
 * 接口桶各一格并阻塞排队。取值只在 Nacos 的 {@code ratelimit.qps}。
 */
@Slf4j
@Service
public class ElongCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<ElongQueryPriceTask>
        implements ElongCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:elong:cps:query-price";

    /**
     * 刷价的用途桶键。本类不手写 acquire——用途由 {@link CallPurpose#REFRESH} 声明、通道层扣格。
     * 这里只读它的生效值打进轮次日志与耗时预估。
     */
    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    /**
     * 艺龙的日期口径是<b>北京时间</b>——它是中国供应商，入住日按 Asia/Shanghai 计。
     * 容器不设 TZ 时 JVM 默认 UTC，那样北京 00:00~08:00 这八小时里 T+0 会算成北京的"昨天"，
     * 既浪费额度又缺上游要的第三天（2026-08-25 实证）。故此处显式指定，不吃 JVM 默认值。
     */
    private static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ElongQueryPriceTaskMapper elongQueryPriceTaskMapper;

    @Resource
    private ElongPriceService elongPriceService;

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
        return SupplierSourceEnum.ELONG;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.elong-cps.enabled", Boolean.class, false);
    }

    /**
     * 档位=住期远近(与飞猪统一):0=T0-2 / 1=T3-7 / 2=T8-30,无货态=N+10(模板偏移算法)。
     * 档 9 是人工停用位,不进本序列故永不消费、不受调档触及。
     * 旧四档(成交/高频/常规/远期)已随 2026-08-28 统一档位退役,旧键 deal/high/normal-* 弃用。
     */
    @Override
    protected List<Integer> tiers() {
        return List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2);
    }

    @Override
    protected int batchSize(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.elong-cps.slow-batch-size", Integer.class, 100);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.elong-cps.mid-batch-size", Integer.class, 200);
            case 2 -> environment.getProperty("task.elong-cps.far-batch-size", Integer.class, 200);
            default -> environment.getProperty("task.elong-cps.batch-size", Integer.class, 400);
        };
    }

    /**
     * 行级并发度。兜底取 1（串行）是安全侧（§3.3.3）：并发是新引入的能力，Nacos 读不到时应退回
     * 已知安全的旧行为——串行只是慢，而并发在连接池未同步放大时会与出价抢连接，出价是真实客流。
     */
    @Override
    protected int concurrency(int priority) {
        if (priority >= SOLD_OUT_OFFSET) {
            return environment.getProperty("task.elong-cps.slow-concurrency", Integer.class, 1);
        }
        return switch (priority) {
            case 1 -> environment.getProperty("task.elong-cps.mid-concurrency", Integer.class, 1);
            case 2 -> environment.getProperty("task.elong-cps.far-concurrency", Integer.class, 6);
            default -> environment.getProperty("task.elong-cps.concurrency", Integer.class, 1);
        };
    }

    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<ElongQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return elongQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /**
     * 每行按这些占用各查一次。高德按 2 人问价时只刷 1 人，2 人查询会如实拿空——
     * 曝光刷新断粮 16h 后被 RATE_DEAD 整体撤下（2026-08-22 事故）。取值为 Nacos 运行时键，可随时调。
     */
    @Override
    protected List<String> dimensions() {
        return Arrays.stream(environment.getProperty("task.elong-cps.occupancies", "1").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(ElongQueryPriceTask row, String dimension) {
        LocalDate today = LocalDate.now(SUPPLIER_ZONE);
        // roomNum 恒 1 是已验证的选择：缓存键不含间数、缓存价也是单间口径，多间在验价与下单侧
        // 乘间数（H001188）。2026-08-24 生产 A/B 实测 hotel.detail 不按 NumberOfRooms 过滤可售
        // 集合与单间价；FAQ 337 亦只要求 NumberOfAdults 与 ChildAges 与 detail 一致，未提间数。
        PriceReq request = PriceReq.builder()
                .adultNum(Integer.parseInt(dimension)).childNum(0)
                .childAges(new ArrayList<>())
                .checkIn(today.plusDays(row.getDelayCheckIn()).toString())
                .checkout(today.plusDays(row.getDelayCheckOut()).toString())
                .roomNum(1).build();
        Supplier supplier = Supplier.builder()
                .supplierId(SupplierSourceEnum.ELONG.getCode())
                .sHotelId(row.getShId()).build();

        List<ProductRespDTO> products = elongPriceService.queryPricesCache(request, supplier);
        if (products == null) {
            // 没问出结果（频控或网络/解析）——不动缓存（F-5.1）
            return RefreshOutcome.FAILED;
        }
        return products.isEmpty() ? RefreshOutcome.EMPTY : RefreshOutcome.ON_SALE;
    }

    @Override
    protected void markRefreshed(ElongQueryPriceTask row) {
        row.setUpdateTime(new Date());
        elongQueryPriceTaskMapper.updateAddCount(row);
    }

    @Override
    protected void adjustPriority(ElongQueryPriceTask row, RefreshOutcome outcome) {
        int target = soldOutOffsetTarget(row.getPriorityLevelNumber(), outcome);
        if (target != row.getPriorityLevelNumber()) {
            elongQueryPriceTaskMapper.updatePriority(row.getId(), target);
        }
    }
}
