package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.ArrayList;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ResponseDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PushProductsReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.inbound.rest.mapping.CancelMapping;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaRegionService;
import com.trip.booking.spa.gateway.application.booking.BookingSyncService;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.cancellation.CancelSyncService;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceSyncService;
import com.trip.booking.spa.gateway.application.order.OrderQuerySyncService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.application.pricing.ProductSyncService;
import com.trip.booking.spa.bootstrap.NacosRuntimeConfig;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.platform.observability.Monitor;

import java.util.Locale;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.gateway.application.routing.Capability;
import com.trip.booking.spa.gateway.application.routing.SupplierCapabilityRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/client/spa")
@Slf4j
public class SpaController {

    @Resource
    private ExpediaRegionService expediaRegionService;

    @Resource
    private NacosRuntimeConfig nacosRuntimeConfig;

    @Autowired
    private PriceCacheService priceCacheService;

    @Resource
    private SupplierCapabilityRegistry capabilityRegistry;

    /**
     * 价格数据
     */
    @PostMapping(value = "/price")
    public ResponseDTO<List<ProductRespDTO>> queryPrice(@RequestBody @Validated PriceReq priceReq) {
        long startTime = System.currentTimeMillis();
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        List<PricingOutcome> outcomes = Lists.newArrayList();
        List<Integer> cachePriceSuppliers = nacosRuntimeConfig.getCachePriceSuppliers();
        Map<Integer, List<String>> cachePriceHotels = nacosRuntimeConfig.getCachePriceHotels();
        // 每条腿先定走缓存还是实时，并把「不支持实时查价」的供应商在派发前挑出来。
        // 以前是串行走到那一腿才返回 unsupported；并行后必须先查，否则别的腿白跑
        List<Supplier> suppliers = priceReq.getSuppliers();
        List<Boolean> useCache = new ArrayList<>(suppliers.size());
        Map<Integer, ProductSyncService> liveServices = new HashMap<>();
        for (Supplier supplier : suppliers) {
            //如果没有传产品id并且配置了供应商查询缓存，则走缓存
            List<String> hotelIdList = cachePriceHotels.getOrDefault(
                    supplier.getSupplierId(), Collections.emptyList());
            boolean cache = StringUtils.isBlank(supplier.getSProductId())
                    && cachePriceSuppliers.contains(supplier.getSupplierId())
                    //查询供应商是全量走缓存还是部分酒店走缓存
                    && (CollectionUtils.isEmpty(hotelIdList) || hotelIdList.contains(supplier.getSHotelId()));
            useCache.add(cache);
            if (!cache && !liveServices.containsKey(supplier.getSupplierId())) {
                ProductSyncService hotelService = capabilityRegistry.find(
                        supplier.getSupplierId(), Capability.PRICING, ProductSyncService.class);
                if (hotelService == null) {
                    return unsupportedSupplierOperation(supplier.getSupplierId(), "price");
                }
                liveServices.put(supplier.getSupplierId(), hotelService);
            }
        }

        // 多条腿并行，结果按下标归位。此前是串行 for：机器内实测 1 条腿 0.9s、5 条腿 4.6~5.0s
        // 线性累加（2026-09-10），上游一页 5 家从美国机跨海过来必撞 5s 预算。各腿互不依赖，
        // 缓存腿不占供应商额度，实时腿各自受自家限流器约束。
        List<Leg> legs = runLegs(priceReq, suppliers, useCache, liveServices);

        // 每腿记一次（O-3.4），失败腿照旧 recordFailedLeg 后整批抛出——与串行时的语义一致：
        // 以前一腿抛就整批 500，现在也是，只是别的腿的指标不再因为它没被记
        RuntimeException firstFailure = null;
        for (Leg leg : legs) {
            String source = leg.cache ? MetricTags.SOURCE_CACHE : MetricTags.SOURCE_LIVE;
            if (leg.failure != null) {
                recordFailedLeg(leg.supplier, source);
                if (firstFailure == null) {
                    firstFailure = leg.failure;
                }
                continue;
            }
            // 缓存读侧如实分态（F-5.1 / F-5.2，2026-08-20）：
            //   有产品     → AVAILABLE
            //   有无货标记 → NO_INVENTORY（刷过、供应商明确答没有；重试无用）
            //   两者皆无   → INDETERMINATE（这一片没刷过，或已过 TTL）
            // 此前三者塌成一态、一律回报「未能确认」。塌了之后：既诱发上游对确定无货的
            // 无谓重试，也让「刷价没覆盖到这个占用片」这类缺口在出价侧完全不可见
            if (leg.cache) {
                if (leg.result.outcome() == PricingOutcome.AVAILABLE) {
                    respDTOList.addAll(leg.result.products());
                }
            } else {
                respDTOList.addAll(leg.result.products());
            }
            outcomes.add(leg.result.outcome());
            recordPriceLeg(leg.supplier, source, leg.result);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }

        Monitor.recordTime(MetricNames.QUERY_PRICE_FOR_SPA, System.currentTimeMillis() - startTime);

        return toPriceResponse(mergeOutcomes(outcomes), respDTOList);
    }

    /**
     * 入口四件套里的请求数与出报数（O-4.2）——此前这个入口只有耗时，「出报率 36%」
     * 这个已知结论没法用指标复现，只能靠 grep 日志现算。
     *
     * <p>腿 = 请求 × 供应商，每腿记一次（O-3.4），outcome 直接沿用 {@link PricingOutcome}
     * 的分态结论，不另造词表。出报条数单独一个名字：它计的是产品条数，和「腿」不是
     * 同一个度量，混在一个 counter 里会把出报率算错。
     */
    /** 查价扇出池名：进 ThreadPools 注册表，水位由 PoolStatsSampler 推成 gauge */
    private static final String LEG_POOL_NAME = "price-leg";

    /** 一条腿的答复：结果或异常二者只有一个非空 */
    private record Leg(Supplier supplier, boolean cache, PricingResult result, RuntimeException failure) {
    }

    /**
     * 并行跑各腿，<b>按请求顺序</b>归位。单腿不进线程池，走原路。
     * 不设整体超时：各腿的耗时上限由各自的供应商客户端 / Redis 客户端超时管，这里只负责别再串行等。
     */
    private List<Leg> runLegs(PriceReq priceReq, List<Supplier> suppliers, List<Boolean> useCache,
                              Map<Integer, ProductSyncService> liveServices) {
        if (suppliers.size() == 1) {
            return List.of(oneLeg(priceReq, suppliers.get(0), useCache.get(0), liveServices));
        }
        List<Callable<Leg>> tasks = new ArrayList<>(suppliers.size());
        for (int i = 0; i < suppliers.size(); i++) {
            Supplier supplier = suppliers.get(i);
            boolean cache = useCache.get(i);
            tasks.add(() -> oneLeg(priceReq, supplier, cache, liveServices));
        }
        List<Leg> out = new ArrayList<>(suppliers.size());
        try {
            for (Future<Leg> f : ThreadPools.virtualPerTask(LEG_POOL_NAME).invokeAll(tasks)) {
                out.add(f.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查价被中断", e);
        } catch (ExecutionException e) {
            // oneLeg 已把 RuntimeException 装进 Leg，走到这里只可能是 Error 级别的问题
            throw new IllegalStateException("查价腿执行异常", e.getCause());
        }
        return out;
    }

    private Leg oneLeg(PriceReq priceReq, Supplier supplier, boolean cache,
                       Map<Integer, ProductSyncService> liveServices) {
        try {
            PricingResult r = cache
                    ? priceCacheService.getPriceResult(priceReq, supplier)
                    : liveServices.get(supplier.getSupplierId()).queryPrice(priceReq, supplier);
            return new Leg(supplier, cache, r, null);
        } catch (RuntimeException e) {
            return new Leg(supplier, cache, null, e);
        }
    }

    private static void recordPriceLeg(Supplier supplier, String source, PricingResult result) {
        SupplierSourceEnum supplierEnum = SupplierSourceEnum.getEnum(supplier.getSupplierId());
        if (supplierEnum == null) {
            return;
        }
        Monitor.recordOne(MetricNames.SPA_PRICE_LEG, MetricTags.leg(supplierEnum, source,
                result.outcome().name().toLowerCase(Locale.ROOT)));
        if (!result.products().isEmpty()) {
            Monitor.recordMany(MetricNames.SPA_PRICE_QUOTED,
                    MetricTags.quoted(supplierEnum, source), result.products().size());
        }
    }

    /** 腿的词表必须穷尽（O-3.3）：异常出去的腿不计数，出报率分母就偏小、算出来偏高 */
    private static void recordFailedLeg(Supplier supplier, String source) {
        SupplierSourceEnum supplierEnum = SupplierSourceEnum.getEnum(supplier.getSupplierId());
        if (supplierEnum == null) {
            return;
        }
        Monitor.recordOne(MetricNames.SPA_PRICE_LEG,
                MetricTags.leg(supplierEnum, source, MetricNames.LEG_ERROR));
    }

    /**
     * 分态落到信封。<b>形状必须与改造前逐字节兼容</b>：上游现有判读只看 HTTP 码与
     * {@code result} 是否为空，故 AVAILABLE/NO_INVENTORY 走 200+数组、INDETERMINATE 走
     * 200+{@code result=null}，与原先一致；新增的只有 {@code outcome} 一个字段。
     */
    static ResponseDTO<List<ProductRespDTO>> toPriceResponse(PricingOutcome outcome,
                                                             List<ProductRespDTO> products) {
        if (outcome == PricingOutcome.AVAILABLE) {
            return ResponseDTO.success(products).withOutcome(outcome);
        }
        if (outcome == PricingOutcome.NO_INVENTORY) {
            // 「确实没有」是一个成功的回答，如实回空列表——上游据此可以告知旅客并停止重试
            return ResponseDTO.success(Collections.<ProductRespDTO>emptyList()).withOutcome(outcome);
        }
        // 「没问出来」才算失败，且 result 必须保持 null：改成空数组会让上游把
        // 「未能确认」读成「确实没有」。errorMsg 也保持原文，避免踩到别处的字符串匹配
        return ResponseDTO.error("result is null").withOutcome(outcome);
    }

    /**
     * 多供应商分态合并。取<b>安全侧</b>：只要还有一家没问出结果，整体就不能说「都没有」。
     *
     * <p>顺序是 {@link PricingOutcome#AVAILABLE} &gt; {@link PricingOutcome#INDETERMINATE}
     * &gt; {@link PricingOutcome#NO_INVENTORY}：有货即有货；无货但有一家没问出来，
     * 整体算没问出来；全部明确无货才是无货。
     *
     * <p>请求里一个供应商都没有时按未能确认——空请求不构成「确实没有」的证据。
     */
    static PricingOutcome mergeOutcomes(List<PricingOutcome> outcomes) {
        if (outcomes.contains(PricingOutcome.AVAILABLE)) {
            return PricingOutcome.AVAILABLE;
        }
        if (outcomes.isEmpty() || outcomes.contains(PricingOutcome.INDETERMINATE)) {
            return PricingOutcome.INDETERMINATE;
        }
        return PricingOutcome.NO_INVENTORY;
    }

    /**
     * 验价
     */
    @PostMapping(value = "/check")
    public ResponseDTO<CheckPriceRespDTO> checkPrice(@RequestBody @Validated CheckPriceReq checkPriceReq) {

        CheckPriceSyncService checkPriceSyncService = capabilityRegistry.find(checkPriceReq.getSupplierId(), Capability.CHECK_PRICE, CheckPriceSyncService.class);
        if (checkPriceSyncService == null) {
            return unsupportedSupplierOperation(checkPriceReq.getSupplierId(), "check");
        }

        CheckPriceRespDTO checkPriceRespDTO = checkPriceSyncService.checkPrice(checkPriceReq);

        if (checkPriceRespDTO == null) {
            // 兜底：模板已保证非空，此处仅防实现绕过模板。不可表达为「不可订」，
            // 否则会把「我们不知道」说成「供应商说没有」
            log.error("checkPrice 返回空，按未能确认回报, sProductId={}", checkPriceReq.getSProductId());
            checkPriceRespDTO = CheckPriceRespDTO.builder()
                    .outcome(CheckPriceOutcome.INDETERMINATE)
                    .message("验价未能确认该产品是否可订，请稍后重试")
                    .build();
        }

        return ResponseDTO.success(checkPriceRespDTO);
    }

    /**
     * 下单。
     *
     * <p>无论供应商侧成功、失败还是结果不确定，本接口一律返回业务成功（success），
     * 由响应体的 {@code outcome} 三态承载真实结果。<b>禁止把「结果不确定」表达为接口错误</b>——
     * 调用方通常把接口错误等同于下单失败并据此退款，而不确定时供应商可能已真实成单。
     * 仅当请求本身不可受理（如供应商不支持下单）时才返回接口错误。
     */
    @PostMapping(value = "/booking")
    public ResponseDTO<BookingRespDTO> booking(@RequestBody @Validated BookingReq bookingReq) {

        BookingSyncService bookingSyncService = capabilityRegistry.find(bookingReq.getSupplierId(), Capability.BOOKING, BookingSyncService.class);
        if (bookingSyncService == null) {
            return unsupportedSupplierOperation(bookingReq.getSupplierId(), "booking");
        }

        BookingRespDTO bookingRespDTO = bookingSyncService.booking(bookingReq);

        if (bookingRespDTO == null) {
            // 兜底：模板已保证非空，此处仅防实现绕过模板。同样不可表达为失败
            log.error("booking 返回空，按结果不确定回报, orderId={}", bookingReq.getOrderId());
            bookingRespDTO = BookingRespDTO.builder()
                    .outcome(BookingOutcome.UNKNOWN)
                    .orderId(bookingReq.getOrderId())
                    .orderDesc("下单结果不确定，请查单确证")
                    .build();
        }

        return ResponseDTO.success(bookingRespDTO);

    }

    /**
     * 取消
     */
    @PostMapping(value = "/cancel")
    public ResponseDTO<CancelRespDTO> cancel(@RequestBody @Validated CancelReq cancelReq) {
        CancelSyncService cancelSyncService = capabilityRegistry.find(cancelReq.getSupplierId(), Capability.CANCELLATION, CancelSyncService.class);
        if (cancelSyncService == null) {
            return unsupportedSupplierOperation(cancelReq.getSupplierId(), "cancel");
        }

        // 取消已切领域模型：JSON↔领域的翻译收在 CancelMapping（①持有翻译，②③不识 JSON）
        CancelResult cancelResult = cancelSyncService.cancel(CancelMapping.toCommand(cancelReq));

        if (cancelResult == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(CancelMapping.toDto(cancelResult));
    }

    /**
     * 查询订单
     */
    @PostMapping(value = "/order")
    public ResponseDTO<OrderRespDTO> orderQuery(@RequestBody @Validated OrderQueryReq orderQueryReq) {

        OrderQuerySyncService orderQuerySyncService = capabilityRegistry.find(orderQueryReq.getSupplierId(), Capability.ORDER_QUERY, OrderQuerySyncService.class);
        if (orderQuerySyncService == null) {
            return unsupportedSupplierOperation(orderQueryReq.getSupplierId(), "order");
        }

        OrderRespDTO orderRespDTO = orderQuerySyncService.orderQuery(orderQueryReq);

        if (orderRespDTO == null) {
            // 兜底：模板已保证非空，此处仅防实现绕过模板。不可表达为「订单不存在」，
            // 否则上游会据此重新下单
            log.error("orderQuery 返回空，按未能确证回报, orderId={}", orderQueryReq.getOrderId());
            orderRespDTO = OrderRespDTO.builder()
                    .presence(OrderPresence.INDETERMINATE)
                    .message("查单未能确证订单是否存在，请稍后重试查单")
                    .build();
        }

        return ResponseDTO.success(orderRespDTO);

    }

    /**
     * 推送产品价格和库存（暂未接入任何分销渠道实现）
     */
    @PostMapping(value = "/push/priceAndInventory")
    public ResponseDTO pushPriceAndInventory(@RequestBody @Validated PushProductsReq pushProductsReq) {
        log.info("调用推送价格库存接口：{}", JsonUtils.writeObject2Json(pushProductsReq));
        return ResponseDTO.success(null);
    }

    /**
     * expedia查询某个城市下所有酒店id
     */
    @GetMapping(value = "/query/expediaHotelIdByCity")
    public ResponseDTO<List<String>> queryExpediaHotelIdByCity(@RequestParam("cityId") String cityId) {
        return ResponseDTO.success(expediaRegionService.queryHotelIdsByRegion(cityId));
    }

    /**
     * 能力发现：供应商 × 能力矩阵。补掉 architecture.md §3.1 的在册缺口——
     * 此前能力是隐式的,上游只能靠实际调用试探某家支不支持某操作。
     */
    @GetMapping(value = "/capabilities")
    public ResponseDTO<java.util.Map<String, Object>> capabilities() {
        return ResponseDTO.success(capabilityRegistry.capabilityMatrix());
    }

    private <T> ResponseDTO<T> unsupportedSupplierOperation(Integer supplierId, String operation) {
        return ResponseDTO.error("supplier operation is not available: supplierId="
                + supplierId + ", operation=" + operation);
    }

}
