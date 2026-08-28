package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.client.QueryProductAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.request.QueryPriceRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaCatalogMapper;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.util.DateUtil;
import com.trip.booking.spa.platform.util.JsonUtils;
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
 * 产品建档：查价响应 → {@code supplier_product_base}（一行=一个卖法）。
 * 流程沿旧链路：默认 +9/+10 天占位日期、零售价+打包价各查一遍、每酒店线程池并发；
 * 占用跟随刷价（{@code task.expedia-cps.occupancies}），每个占用各建一遍。
 *
 * <p>2026-08-20 起不再双推——聚合域的桥按 R-6.1 不放在供应商网关，已撤表。
 *
 * <p><b>身份语义（docs/product-identity.md）</b>：目录行的身份列存 productKey（稳定"卖法"，
 * R-1.1），供应商真码 rate.id 只进 {@code supplier_quote_hint}（解析快速通道，R-2.3）——
 * 此前直接把 rate.id 当 productId 存，是把令牌当身份（§6.2 禁止的形态）。三条纪律：
 * <ul>
 *   <li>键必须经 {@link ExpediaProductKeyDeriver#deriveProductKey} 派生——建档与查价/resolve
 *       同一份代码，键分叉即身份分叉</li>
 *   <li>占用是键成分：一行只代表该占用下的卖法，没建的占用取不到</li>
 *   <li>餐食/退改解析不出（UNKNOWN）不进目录（R-5.4），宁缺不污染等价类</li>
 * </ul>
 */
@Slf4j
@Service
public class ExpediaProductMappingService {

    private static final int SUPPLIER_ID = SupplierSourceEnum.EXPEDIA.getCode();
    private static final String OPERATOR = "expedia-transform";
    private static final int PAGE_SIZE = 100;

    /**
     * 建档占用<b>跟随刷价</b>，共用 {@code task.expedia-cps.occupancies} 这一个键——
     * 与艺龙同模式：刷什么就建什么档，不另立口径。
     *
     * <p>本类原先有独立的 {@code task.expedia-catalog.occupancies}，那是两套口径，
     * 一旦与刷价配得不一致，建出来的行就与真实流量的 productKey 不相等、取不到
     * （2026-08-28 生产实测：建档 occupancy=1 而刷价 2，同表两套互不相交的键）。
     * 兜底与刷价侧同为 "2"（改为配置项之前的写死值）。
     */
    @Value("${task.expedia-cps.occupancies:2}")
    private String catalogOccupancies;

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

    /** 产品档案走供应商通用写入口；ExpediaCatalogMapper 只留 Expedia 专属的表操作 */
    @javax.annotation.Resource
    private com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper productCatalogMapper;

    /** 键派生与餐食/退改规范化的唯一权威——建档的键必须与查价/resolve 逐字节同口径 */
    @Resource
    private ExpediaProductKeyDeriver productKeyDeriver;

    /**
     * 照抄旧 saveOrUpdateProductInfo：指定酒店或分页全量
     *
     * <p><b>占用必须覆盖实际被查的那些</b>：occupancy 是 productKey 的成分，目录只按
     * {@code product_key} <b>精确相等</b>取用（{@code selectAttributesByProductKeys}），
     * 故没建的占用一律取不到。本方法原先写死 {@code occupancies=["1"]}（旧实现遗留），
     * 而刷价走 2 人（{@code ExpediaCPSQueryPriceServiceImpl.dimensions()}），两边各建各的、
     * 键互不相交。现与刷价共用 {@code task.expedia-cps.occupancies} 逐个建。
     *
     * @param checkInDate  查价占位入住日；空=+9 天（旧默认）
     * @param checkOutDate 查价占位离店日；空=+10 天
     * @param supplierHotelIds 指定酒店；空=分页遍历 supplier_hotel_base
     * @param startNum     分页起始页（断点续跑）
     * @param occupancies0 查价占用集，逗号分隔；空=跟随刷价的 {@code task.expedia-cps.occupancies}
     * @return 已提交建档的酒店数（产品在后台线程落库）
     */
    public int syncProducts(String checkInDate, String checkOutDate, List<String> supplierHotelIds,
                            Integer startNum, String occupancies0) {
        List<String> occupancies = java.util.Arrays.stream(
                        (StringUtils.isBlank(occupancies0) ? catalogOccupancies : occupancies0).split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(java.util.stream.Collectors.toList());
        // R-4.3：只有房型 ID 申报为稳定的供应商才许进房型级目录。Expedia 已核验；
        // 这里仍显式过闸，防止此类被当模板复制给未申报的供应商
        if (!SupplierIdentityProfile.forCode(SUPPLIER_ID).catalogEligibleAtRoomLevel()) {
            throw new IllegalStateException("供应商 " + SUPPLIER_ID
                    + " 房型 ID 未核验稳定，禁止进房型级目录（docs/product-identity.md R-4.3）");
        }
        boolean blankDates = StringUtils.isBlank(checkInDate) || StringUtils.isBlank(checkOutDate);
        final String checkIn = blankDates ? DateUtil.getFutureDay(null, 9) : checkInDate;
        final String checkOut = blankDates ? DateUtil.getFutureDay(null, 10) : checkOutDate;
        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
            occupancies.forEach(o -> pushProductInfo(checkIn, checkOut, supplierHotelIds, o));
            return supplierHotelIds.size();
        }
        AtomicInteger submitted = new AtomicInteger();
        int pageNum = startNum == null ? 0 : startNum;
        while (true) {
            final List<String> page = catalogMapper.selectSupplierHotelIds(SUPPLIER_ID, pageNum * PAGE_SIZE, PAGE_SIZE);
            if (CollectionUtils.isEmpty(page)) {
                break;
            }
            occupancies.forEach(o -> pushProductInfo(checkIn, checkOut, page, o));
            submitted.addAndGet(page.size());
            pageNum++;
        }
        return submitted.get();
    }

    /** 照抄旧 pushProductInfo：每酒店一个线程，零售价+打包价各建档一遍 */
    private void pushProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds,
                                 String occupancy) {
        supplierHotelIds.forEach(supplierHotelId -> ThreadPools.fixedCallerRuns(ExpediaGeographyIngestionService.CONTENT_POOL_NAME, 20, 1000).execute(() -> {
            QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder()
                    .property_id(supplierHotelId)
                    .checkin(checkInDate)
                    .checkout(checkOutDate)
                    .currency("USD")
                    .occupancies(List.of(occupancy))
                    .sales_environment("hotel_only")
                    .build();
            try {
                ResponseResult<QueryPriceResponse> resultOnly = new QueryProductAccess(
                        host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter)
                        .access(queryPriceRequest, CallPurpose.CONTENT);
                persistProducts(resultOnly, queryPriceRequest);
                // 查询打包价（照旧：同一请求对象切环境再查一遍）
                queryPriceRequest.setSales_environment("hotel_package");
                ResponseResult<QueryPriceResponse> resultPackage = new QueryProductAccess(
                        host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter)
                        .access(queryPriceRequest, CallPurpose.CONTENT);
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
        AtomicInteger upserted = new AtomicInteger();

        result.getData().getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isEmpty(hotelPrice.getRooms())) {
                return;
            }
            hotelPrice.getRooms().forEach(room -> room.getRates().forEach(rate -> {
                Meal meal = productKeyDeriver.convertMeal(adults, rate.getAmenities());
                List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges())
                        ? List.of(CancelPolicy.builder().cancelType(0).build())
                        : productKeyDeriver.convertCancelPolicy(request.getCheckin(), rate.getCancel_penalties());
                // R-5.4：餐食/退改 UNKNOWN 的不进目录——判据只能问派生器（R-2.8）：
                // 空列表之外还有「阶梯在但判不出全款」的第三种 UNKNOWN，从列表判空看不出来
                if (!productKeyDeriver.isCatalogEligible(meal, cancelPolicy)) {
                    skippedUnknown.incrementAndGet();
                    return;
                }
                ProductIdentity identity = productKeyDeriver.deriveIdentity(
                        hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, occupancy);

                // 全部照抄派生器的产物（R-2.8）：本处一个判定都不做。
                // 原先把 Meal/CancelPolicy 重判一遍压成 breakfast/cancelType 两个 int，
                // 既降维（B1L1D1 与 B1L0D0 同为 1、占用无处安放）又与派生器分叉。
                // 房型层的 has_window 与聚合域的 product_id/room_id/hotel_id 已随表重设计移除
                // （R-2.9 / R-6.1）。
                HashMap<String, Object> p = new HashMap<>();
                p.put("supplierId", SUPPLIER_ID);
                p.put("productKey", identity.productKey());
                p.put("supplierAccount", identity.account());
                p.put("supplierHotelId", identity.supplierHotelId());
                p.put("supplierRoomId", identity.supplierRoomId());
                p.put("mealSignature", identity.mealSignature());
                p.put("cancelClass", identity.cancelClass());
                p.put("occupancy", identity.occupancy());
                p.put("supplierProductName", room.getRoom_name());
                // rate.id 是报价标识，只当快速通道（R-2.3）；申报稳定才可填
                p.put("supplierQuoteHint", quoteCodeStable ? rate.getId() : null);
                p.put("operator", OPERATOR);
                productCatalogMapper.upsertSupplierProductBase(p);
                upserted.incrementAndGet();
            }));
        });
        // 每酒店必打一行成果——全量建档 9.7 万家时，进度与产出全靠这行可检索
        log.info("产品建档：hotel={},salesEnv={},写入 {} 条(等价卖法在库内归并),UNKNOWN 跳过 {} 条",
                request.getProperty_id(), request.getSales_environment(), upserted.get(), skippedUnknown.get());
    }

}
