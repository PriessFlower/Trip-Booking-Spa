package com.bingo.hotel.spa.intl.core.feign.impl;

import com.bingo.hotel.spa.intl.cli.SPAFeignClient;
import com.bingo.hotel.spa.intl.cli.dto.BookingRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CancelRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.OrderRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ResponseDTO;
import com.bingo.hotel.spa.intl.cli.seq.BookingReq;
import com.bingo.hotel.spa.intl.cli.seq.CancelReq;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.OrderQueryReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PushProductsReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaStaticInfoService;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.api.service.BookingSyncService;
import com.bingo.hotel.spa.intl.core.api.service.CachePriceService;
import com.bingo.hotel.spa.intl.core.api.service.CancelSyncService;
import com.bingo.hotel.spa.intl.core.api.service.CheckPriceSyncService;
import com.bingo.hotel.spa.intl.core.api.service.OrderQuerySyncService;
import com.bingo.hotel.spa.intl.core.api.service.ProductSyncService;
import com.bingo.hotel.spa.intl.core.monitor.Monitor;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.bingo.hotel.spa.intl.core.util.SpringAppContextUtil;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/client/spa")
@Slf4j
public class HotelFeignClientImpl implements SPAFeignClient {

    @Resource
    private ExpediaStaticInfoService expediaStaticInfoService;

    @Resource
    private RateHawkService rateHawkService;

    //走缓存的供应商配置
    @ApolloJsonValue("${querty.cache.supplier}")
    private List<String> queryCacheSupplier;

    @Autowired
    private CachePriceService cachePriceService;

    @Override
    @PostMapping(value = "/price")
    public ResponseDTO<List<ProductRespDTO>> queryPrice(@RequestBody @Validated PriceReq priceReq) {
        long startTime = System.currentTimeMillis();
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        for (Supplier supplier : priceReq.getSuppliers()) {
            //如果没有传产品id并且配置了供应商查询缓存，则走缓存
            if(StringUtils.isBlank(supplier.getSProductId())
                    && !CollectionUtils.isEmpty(queryCacheSupplier)
                    && queryCacheSupplier.contains(supplier.getSupplierId().toString())){
                List<ProductRespDTO> price = cachePriceService.getPrice(priceReq, supplier);
                if (CollectionUtils.isNotEmpty(price)) {
                    respDTOList.addAll(price);
                }
            }else{
                //实时查询
                ProductSyncService hotelService = SpringAppContextUtil.AppContext.getBean(
                        SupplierSourceEnum.getEnum(supplier.getSupplierId()).getDesc()
                                + "ProductSyncService");
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

    @Override
    @PostMapping(value = "/check")
    public ResponseDTO<CheckPriceRespDTO> checkPrice(@RequestBody @Validated CheckPriceReq checkPriceReq) {

        CheckPriceSyncService checkPriceSyncService = SpringAppContextUtil.AppContext.getBean(
                SupplierSourceEnum.getEnum(checkPriceReq.getSupplierId()).getDesc()
                        + "CheckPriceSyncService");

        CheckPriceRespDTO checkPriceRespDTO = checkPriceSyncService.checkPrice(checkPriceReq);

        if (checkPriceRespDTO == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(checkPriceRespDTO);
    }

    @Override
    @PostMapping(value = "/booking")
    public ResponseDTO<BookingRespDTO> booking(@RequestBody @Validated BookingReq bookingReq) {

        BookingSyncService bookingSyncService = SpringAppContextUtil.AppContext.getBean(
                SupplierSourceEnum.getEnum(bookingReq.getSupplierId()).getDesc()
                        + "BookingSyncService");

        BookingRespDTO bookingRespDTO = bookingSyncService.booking(bookingReq);

        if (bookingReq == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(bookingRespDTO);

    }

    @Override
    @PostMapping(value = "/cancel")
    public ResponseDTO<CancelRespDTO> cancel(CancelReq cancelReq) {
        CancelSyncService cancelSyncService = SpringAppContextUtil.AppContext.getBean(
                SupplierSourceEnum.getEnum(cancelReq.getSupplierId()).getDesc()
                        + "CancelSyncService");

        CancelRespDTO cancelRespDTO = cancelSyncService.cancel(cancelReq);

        if (cancelReq == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(cancelRespDTO);
    }

    @Override
    @PostMapping(value = "/order")
    public ResponseDTO<OrderRespDTO> orderQuery(OrderQueryReq orderQueryReq) {

        OrderQuerySyncService orderQuerySyncService = SpringAppContextUtil.AppContext.getBean(
                SupplierSourceEnum.getEnum(orderQueryReq.getSupplierId()).getDesc()
                        + "OrderQuerySyncService");

        OrderRespDTO orderRespDTO = orderQuerySyncService.orderQuery(orderQueryReq);

        if (orderQueryReq == null) {
            return ResponseDTO.error("result is null");
        }

        return ResponseDTO.success(orderRespDTO);

    }

    @Override
    @PostMapping(value = "/push/priceAndInventory")
    public ResponseDTO pushPriceAndInventory(PushProductsReq pushProductsReq) {
        log.info("调用推送价格库存接口：{}", JsonUtils.writeObject2Json(pushProductsReq));
//        ProductPushService productPushService = SpringAppContextUtil.AppContext.getBean(
//                DistributorSourceEnum.getEnum(pushProductsReq.getDistributorId()).getDesc()
//                        + "ProductPushService");
//        productPushService.pushPriceAndInventory(pushProductsReq.getPushProductsDTO());
        return ResponseDTO.success(null);
    }

    @Override
    @GetMapping(value = "/query/expediaHotelIdByCity")
    public ResponseDTO<List<String>> queryExpediaHotelIdByCity(@RequestParam("cityId") String cityId) {
        return ResponseDTO.success(expediaStaticInfoService.queryHotelIdByCity(cityId));
    }

}
