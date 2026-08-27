package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.FliggyQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
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
 * 飞猪刷价：消费 {@code fliggy_query_price_task}，逐店 ari.availability 写入价格缓存。
 * 调度骨架在 {@link AbstractCPSQueryPriceService}；飞猪起步<b>单档 0</b>（无历史分档包袱），
 * 速率由 Nacos 的 REFRESH 用途桶约束，通道层扣格。
 */
@Slf4j
@Service
public class FliggyCPSQueryPriceServiceImpl extends AbstractCPSQueryPriceService<FliggyQueryPriceTask>
        implements FliggyCPSQueryPriceService {

    private static final String LOCK_KEY = "lock:fliggy:cps:query-price";

    private static final String REFRESH_LIMIT_KEY =
            "GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH";

    /** 飞猪按北京时间计入住日（中国供应商），不吃 JVM 默认时区——理由同艺龙 */
    private static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private FliggyQueryPriceTaskMapper fliggyQueryPriceTaskMapper;
    @Resource
    private FliggyPriceServiceImpl fliggyPriceService;
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
        return SupplierSourceEnum.FLIGGY;
    }

    @Override
    protected boolean gateOpen() {
        return environment.getProperty("task.fliggy-cps.enabled", Boolean.class, false);
    }

    @Override
    protected List<Integer> tiers() {
        return List.of(0);
    }

    @Override
    protected int batchSize(int priority) {
        return environment.getProperty("task.fliggy-cps.batch-size", Integer.class, 200);
    }

    /** 兜底串行是安全侧（§3.3.3）：并发是能力，Nacos 读不到时退回已知安全的慢 */
    @Override
    protected int concurrency(int priority) {
        return environment.getProperty("task.fliggy-cps.concurrency", Integer.class, 1);
    }

    @Override
    protected double declaredQps(int priority) {
        if (!rateLimitProperties.isRegistered(REFRESH_LIMIT_KEY)) {
            double interfaceQps = rateLimitProperties.qpsOf("GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_PRODUCT_PRICES");
            log.error("[gate] 刷价用途桶 {} 未登记，将按接口桶 {} QPS 跑满、不给客流留头 —— "
                    + "请到 Nacos 的 ratelimit.qps 补齐该键", REFRESH_LIMIT_KEY, interfaceQps);
            return interfaceQps;
        }
        return rateLimitProperties.qpsOf(REFRESH_LIMIT_KEY);
    }

    @Override
    protected List<FliggyQueryPriceTask> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
        return fliggyQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
    }

    /** 占用维度。默认 2 人（主流查询口径），与艺龙同为 Nacos 运行时键可随时调 */
    @Override
    protected List<String> dimensions() {
        return Arrays.stream(environment.getProperty("task.fliggy-cps.occupancies", "2").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected RefreshOutcome refreshOne(FliggyQueryPriceTask row, String dimension) {
        LocalDate today = LocalDate.now(SUPPLIER_ZONE);
        PriceReq request = PriceReq.builder()
                .adultNum(Integer.parseInt(dimension)).childNum(0)
                .childAges(new ArrayList<>())
                .checkIn(today.plusDays(row.getDelayCheckIn()).toString())
                .checkout(today.plusDays(row.getDelayCheckOut()).toString())
                .roomNum(1).build();
        Supplier supplier = Supplier.builder()
                .supplierId(SupplierSourceEnum.FLIGGY.getCode())
                .sHotelId(row.getShId()).build();

        List<ProductRespDTO> products = fliggyPriceService.queryPricesCache(request, supplier);
        if (products == null) {
            return RefreshOutcome.FAILED;
        }
        return products.isEmpty() ? RefreshOutcome.EMPTY : RefreshOutcome.ON_SALE;
    }

    @Override
    protected void markRefreshed(FliggyQueryPriceTask row) {
        row.setUpdateTime(new Date());
        fliggyQueryPriceTaskMapper.updateAddCount(row);
    }
}
