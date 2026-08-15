package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
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
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaGeographyIngestionService;
import com.trip.booking.spa.gateway.application.booking.BookingSyncService;
import com.trip.booking.spa.gateway.application.pricing.CachePriceService;
import com.trip.booking.spa.gateway.application.cancellation.CancelSyncService;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceSyncService;
import com.trip.booking.spa.gateway.application.order.OrderQuerySyncService;
import com.trip.booking.spa.gateway.application.pricing.ProductSyncService;
import com.trip.booking.spa.bootstrap.NacosRuntimeConfig;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.platform.util.SpringAppContextUtil;
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
    private ExpediaGeographyIngestionService expediaGeographyIngestionService;

    @Resource
    private NacosRuntimeConfig nacosRuntimeConfig;

    @Autowired
    private CachePriceService cachePriceService;

    /**
     * 价格数据
     */
    @PostMapping(value = "/price")
    public ResponseDTO<List<ProductRespDTO>> queryPrice(@RequestBody @Validated PriceReq priceReq) {
        long startTime = System.currentTimeMillis();
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        List<Integer> cachePriceSuppliers = nacosRuntimeConfig.getCachePriceSuppliers();
        Map<Integer, List<String>> cachePriceHotels = nacosRuntimeConfig.getCachePriceHotels();
        for (Supplier supplier : priceReq.getSuppliers()) {
            //如果没有传产品id并且配置了供应商查询缓存，则走缓存
            List<String> hotelIdList = cachePriceHotels.getOrDefault(
                    supplier.getSupplierId(), Collections.emptyList());
            if (StringUtils.isBlank(supplier.getSProductId())
                    && cachePriceSuppliers.contains(supplier.getSupplierId())
                    //查询供应商是全量走缓存还是部分酒店走缓存
                    && (CollectionUtils.isEmpty(hotelIdList) || hotelIdList.contains(supplier.getSHotelId()))) {
                List<ProductRespDTO> price = cachePriceService.getPrice(priceReq, supplier);
                if (CollectionUtils.isNotEmpty(price)) {
                    respDTOList.addAll(price);
                }
            } else {
                //实时查询
                ProductSyncService hotelService = findSupplierService(
                        supplier.getSupplierId(), "ProductSyncService", ProductSyncService.class);
                if (hotelService == null) {
                    return unsupportedSupplierOperation(supplier.getSupplierId(), "price");
                }
                List<ProductRespDTO> list = hotelService.queryPrice(priceReq, supplier);

                if (CollectionUtils.isNotEmpty(list)) {
                    respDTOList.addAll(list);
                }
            }

        }

        Monitor.recordTime("query_price_for_spa", System.currentTimeMillis() - startTime);

        if (CollectionUtils.isEmpty(respDTOList)) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(respDTOList);
    }

    /**
     * 验价
     */
    @PostMapping(value = "/check")
    public ResponseDTO<CheckPriceRespDTO> checkPrice(@RequestBody @Validated CheckPriceReq checkPriceReq) {

        CheckPriceSyncService checkPriceSyncService = findSupplierService(
                checkPriceReq.getSupplierId(), "CheckPriceSyncService", CheckPriceSyncService.class);
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

        BookingSyncService bookingSyncService = findSupplierService(
                bookingReq.getSupplierId(), "BookingSyncService", BookingSyncService.class);
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
        CancelSyncService cancelSyncService = findSupplierService(
                cancelReq.getSupplierId(), "CancelSyncService", CancelSyncService.class);
        if (cancelSyncService == null) {
            return unsupportedSupplierOperation(cancelReq.getSupplierId(), "cancel");
        }

        CancelRespDTO cancelRespDTO = cancelSyncService.cancel(cancelReq);

        if (cancelRespDTO == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(cancelRespDTO);
    }

    /**
     * 查询订单
     */
    @PostMapping(value = "/order")
    public ResponseDTO<OrderRespDTO> orderQuery(@RequestBody @Validated OrderQueryReq orderQueryReq) {

        OrderQuerySyncService orderQuerySyncService = findSupplierService(
                orderQueryReq.getSupplierId(), "OrderQuerySyncService", OrderQuerySyncService.class);
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
        return ResponseDTO.success(expediaGeographyIngestionService.queryHotelIdsByRegion(cityId));
    }

    private <T> T findSupplierService(Integer supplierId, String serviceSuffix, Class<T> serviceType) {
        SupplierSourceEnum supplier = supplierId == null ? null : SupplierSourceEnum.getEnum(supplierId);
        ApplicationContext applicationContext = SpringAppContextUtil.AppContext.getApplicationContextHolder();
        if (supplier == null || applicationContext == null) {
            return null;
        }

        String beanName = supplier.getDesc() + serviceSuffix;
        if (!applicationContext.containsBean(beanName)) {
            return null;
        }
        return applicationContext.getBean(beanName, serviceType);
    }

    private <T> ResponseDTO<T> unsupportedSupplierOperation(Integer supplierId, String operation) {
        return ResponseDTO.error("supplier operation is not available: supplierId="
                + supplierId + ", operation=" + operation);
    }

}
