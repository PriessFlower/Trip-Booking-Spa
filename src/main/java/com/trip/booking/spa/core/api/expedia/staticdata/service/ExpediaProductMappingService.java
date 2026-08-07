package com.trip.booking.spa.core.api.expedia.staticdata.service;

import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.expedia.access.QueryProductAccess;
import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryPriceResponse;
import com.trip.booking.spa.core.api.expedia.mapper.ExpediaCatalogMapper;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
import com.trip.booking.spa.core.api.expedia.utils.ThreadPoolUtils;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.DateUtil;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 产品映射建档：查价响应 → global_product_supplier（目录域）+ supplier_product_base（档案域）。
 * 流程照抄旧链路 saveOrUpdateProductInfo/pushProductInfo：
 * 默认 +9/+10 天占位日期、occupancy=1、零售价+打包价各查一遍、每酒店线程池并发、双推。
 * 适配点：分页源从中台 queryHotelPageList 改为自家 supplier_hotel_base 表；推中台改为写还原表。
 */
@Slf4j
@Service
public class ExpediaProductMappingService {

    private static final int SUPPLIER_ID = SupplierSourceEnum.EXPEDIA.getCode();
    private static final int PAGE_SIZE = 100;

    @Value("${expedia.url.host}")
    private String host;
    @Value("${expedia.session}")
    private String sessionId;
    @Value("${expedia.ownIp}")
    private String ownIp;
    @Value("${expedia.partner_point_of_sale}")
    private String partnerPointOfSale;
    @Value("${expedia.payment_terms}")
    private String paymentTerms;
    @Value("${expedia.billing_terms}")
    private String billingTerms;

    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;
    @Resource
    private ExpediaCatalogMapper catalogMapper;

    /**
     * 照抄旧 saveOrUpdateProductInfo：指定酒店或分页全量
     *
     * @param checkInDate  查价占位入住日；空=+9 天（旧默认）
     * @param checkOutDate 查价占位离店日；空=+10 天
     * @param supplierHotelIds 指定酒店；空=分页遍历 supplier_hotel_base
     * @param startNum     分页起始页（断点续跑）
     * @return 已提交建档的酒店数（产品在后台线程落库）
     */
    public int syncProducts(String checkInDate, String checkOutDate, List<String> supplierHotelIds, Integer startNum) {
        if (StringUtils.isBlank(checkInDate) || StringUtils.isBlank(checkOutDate)) {
            checkInDate = DateUtil.getFutureDay(null, 9);
            checkOutDate = DateUtil.getFutureDay(null, 10);
        }
        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
            pushProductInfo(checkInDate, checkOutDate, supplierHotelIds);
            return supplierHotelIds.size();
        }
        AtomicInteger submitted = new AtomicInteger();
        int pageNum = startNum == null ? 0 : startNum;
        while (true) {
            List<String> page = catalogMapper.selectSupplierHotelIds(SUPPLIER_ID, pageNum * PAGE_SIZE, PAGE_SIZE);
            if (CollectionUtils.isEmpty(page)) {
                break;
            }
            pushProductInfo(checkInDate, checkOutDate, page);
            submitted.addAndGet(page.size());
            pageNum++;
        }
        return submitted.get();
    }

    /** 照抄旧 pushProductInfo：每酒店一个线程，零售价+打包价各建档一遍 */
    private void pushProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds) {
        supplierHotelIds.forEach(supplierHotelId -> ThreadPoolUtils.execute(() -> {
            QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                    .property_id(supplierHotelId)
                    .checkin(checkInDate)
                    .checkout(checkOutDate)
                    .currency("USD")
                    .occupancies(List.of("1"))
                    .sales_environment("hotel_only")
                    .billing_terms(billingTerms)
                    .payment_terms(paymentTerms)
                    .partner_point_of_sale(partnerPointOfSale)
                    .build();
            try {
                ResponseResult<QueryPriceResponse> resultOnly = new QueryProductAccess(
                        host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter)
                        .access(queryPriceRequest);
                persistProducts(resultOnly, queryPriceRequest);
                // 查询打包价（照旧：同一请求对象切环境再查一遍）
                queryPriceRequest.setSales_environment("hotel_package");
                ResponseResult<QueryPriceResponse> resultPackage = new QueryProductAccess(
                        host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter)
                        .access(queryPriceRequest);
                persistProducts(resultPackage, queryPriceRequest);
            } catch (Exception e) {
                log.error("产品建档异常：request:{}", JsonUtils.writeObject2Json(queryPriceRequest), e);
            }
        }));
    }

    /** 照抄旧 transformBaseProductReq/transformInfoProductReq 的字段语义，双推还原表 */
    private void persistProducts(ResponseResult<QueryPriceResponse> result, QueryPriceRequest request) {
        if (result == null || result.getData() == null
                || CollectionUtils.isEmpty(result.getData().getHotelPrices())) {
            log.info("产品建档查价无结果：request:{}", JsonUtils.writeObject2Json(request));
            return;
        }
        result.getData().getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isEmpty(hotelPrice.getRooms())) {
                return;
            }
            hotelPrice.getRooms().forEach(room -> room.getRates().forEach(rate -> {
                HashMap<String, Object> p = new HashMap<>();
                // Expedia 打底：统一 product/room/hotel id = 供应商侧 id（1:1）
                p.put("productId", rate.getId());
                p.put("roomId", room.getId());
                p.put("hotelId", hotelPrice.getProperty_id());
                p.put("supplierId", SUPPLIER_ID);
                p.put("supplierHotelId", hotelPrice.getProperty_id());
                p.put("supplierRoomId", room.getId());
                p.put("supplierProductId", rate.getId());
                p.put("supplierProductName", room.getRoom_name());
                // 照旧：三个属性占位 0
                p.put("hasWindow", 0);
                p.put("breakfast", 0);
                p.put("cancelType", 0);
                catalogMapper.upsertGlobalProductSupplier(p);
                catalogMapper.upsertSupplierProductBase(p);
            }));
        });
    }
}
