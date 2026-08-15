package com.trip.booking.spa.core.api.expedia.staticdata.service;

import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.identity.SupplierIdentityProfile;
import com.trip.booking.spa.core.api.dto.CancelPolicy;
import com.trip.booking.spa.core.api.dto.Meal;
import com.trip.booking.spa.core.api.expedia.service.impl.ExpediaPriceServiceImpl;
import com.trip.booking.spa.core.api.expedia.access.QueryProductAccess;
import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryPriceResponse;
import com.trip.booking.spa.core.api.expedia.config.ExpediaContractProfile;
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
 * 流程沿旧链路：默认 +9/+10 天占位日期、occupancy=1、零售价+打包价各查一遍、每酒店线程池并发、双推。
 *
 * <p><b>身份语义（docs/product-identity.md）</b>：目录行的身份列存 productKey（稳定"卖法"，
 * R-1.1），供应商真码 rate.id 只进 {@code supplier_quote_hint}（解析快速通道，R-2.3）——
 * 此前直接把 rate.id 当 productId 存，是把令牌当身份（§6.2 禁止的形态）。三条纪律：
 * <ul>
 *   <li>键必须经 {@link ExpediaPriceServiceImpl#deriveProductKey} 派生——建档与查价/resolve
 *       同一份代码，键分叉即身份分叉</li>
 *   <li>占用是键成分：本档以建档请求的 occupancy（默认 1）派生，目录行只代表该占用下的卖法</li>
 *   <li>餐食/退改解析不出（UNKNOWN）不进目录（R-5.4），宁缺不污染等价类</li>
 * </ul>
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

    /** 合同车道参数的唯一来源；与查价链路共用，避免建档与查价走上不同车道 */
    @Resource
    private ExpediaContractProfile contractProfile;

    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;
    @Resource
    private ExpediaCatalogMapper catalogMapper;

    /**
     * 键派生与餐食/退改规范化的唯一权威（deriveProductKey / convertMeal / convertCancelPolicy）。
     * 有意注入实现类而非接口：建档的键必须与查价/resolve 逐字节同口径，不允许第二份实现。
     */
    @Resource
    private ExpediaPriceServiceImpl priceService;

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
        // R-4.3：只有房型 ID 申报为稳定的供应商才许进房型级目录。Expedia 已核验；
        // 这里仍显式过闸，防止此类被当模板复制给未申报的供应商
        if (!SupplierIdentityProfile.forCode(SUPPLIER_ID).catalogEligibleAtRoomLevel()) {
            throw new IllegalStateException("供应商 " + SUPPLIER_ID
                    + " 房型 ID 未核验稳定，禁止进房型级目录（docs/product-identity.md R-4.3）");
        }
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
            QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder()
                    .property_id(supplierHotelId)
                    .checkin(checkInDate)
                    .checkout(checkOutDate)
                    .currency("USD")
                    .occupancies(List.of("1"))
                    .sales_environment("hotel_only")
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

    /** 双推目录域与档案域；字段语义见类注释（身份=productKey，真码=hint） */
    private void persistProducts(ResponseResult<QueryPriceResponse> result, QueryPriceRequest request) {
        if (result == null || result.getData() == null
                || CollectionUtils.isEmpty(result.getData().getHotelPrices())) {
            log.info("产品建档查价无结果：request:{}", JsonUtils.writeObject2Json(request));
            return;
        }
        String occupancy = request.getOccupancies().get(0);
        // 占用串首段即成人数（如 "2-9,4" → 2）；餐食份数按它换算，与查价链路同口径
        int adults = Integer.parseInt(occupancy.split("-")[0]);
        boolean quoteCodeStable = SupplierIdentityProfile.forCode(SUPPLIER_ID).quoteCodeStability()
                == SupplierIdentityProfile.QuoteCodeStability.STABLE;
        AtomicInteger skippedUnknown = new AtomicInteger();

        result.getData().getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isEmpty(hotelPrice.getRooms())) {
                return;
            }
            hotelPrice.getRooms().forEach(room -> room.getRates().forEach(rate -> {
                Meal meal = priceService.convertMeal(adults, rate.getAmenities());
                List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges())
                        ? List.of(CancelPolicy.builder().cancelType(0).build())
                        : priceService.convertCancelPolicy(request.getCheckin(), rate.getCancel_penalties());
                // R-5.4：餐食/退改解析不出的不进目录——UNKNOWN 进了目录就会污染等价类匹配
                if (meal == null || CollectionUtils.isEmpty(cancelPolicy)) {
                    skippedUnknown.incrementAndGet();
                    return;
                }
                String productKey = priceService.deriveProductKey(
                        hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, occupancy);

                HashMap<String, Object> p = new HashMap<>();
                // Expedia 打底：统一侧 = 供应商侧（1:1）。统一侧列属聚合域，将来可改（R-2.4）
                p.put("productId", productKey);
                p.put("roomId", room.getId());
                p.put("hotelId", hotelPrice.getProperty_id());
                p.put("supplierId", SUPPLIER_ID);
                p.put("supplierHotelId", hotelPrice.getProperty_id());
                p.put("supplierRoomId", room.getId());
                // 身份列（唯一键）：productKey。rate.id 是报价标识，只当快速通道（R-2.3）
                p.put("supplierProductId", productKey);
                p.put("supplierQuoteHint", quoteCodeStable ? rate.getId() : null);
                p.put("supplierProductName", room.getRoom_name());
                // 有窗是房型层事实（supplier_room_base.hasWindows 已有），产品层保持占位
                p.put("hasWindow", 0);
                p.put("breakfast", isPositive(meal.getCount()) ? 1 : 0);
                p.put("cancelType", cancelPolicy.stream()
                        .anyMatch(c -> Integer.valueOf(1).equals(c.getCancelType())) ? 1 : 0);
                catalogMapper.upsertGlobalProductSupplier(p);
                catalogMapper.upsertSupplierProductBase(p);
            }));
        });
        if (skippedUnknown.get() > 0) {
            log.info("产品建档：{} 条报价餐食/退改解析不出，按 R-5.4 未入目录,hotel={}",
                    skippedUnknown.get(), request.getProperty_id());
        }
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }
}
