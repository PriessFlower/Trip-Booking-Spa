package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.checkprice.client.DataValidateAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.client.HotelDetailAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongRestCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongHotelDetailRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongDataValidateResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.product.ResolveGate;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 艺龙查价/验价协议逻辑。第一家按移植标准（docs/product-identity.md §7 阶段4）
 * 生在新管线上的 cursor 供应商：申报（SupplierIdentityProfile.ELONG）→ 适配层两钩子
 * （{@link ElongProductKeyDeriver}）→ productKey/resolve/OfferStore 全复用。
 *
 * <p><b>现取现验（R-3.1）</b>：验价第一步永远是重打一次 hotel.detail 现取新马甲与
 * 报价码，绝不复用任何早前会话的凭证——cursor 复用验价缓存里的死马甲，实测隔时重放
 * 45/47 全灭（H001144），是其失败率的头号病灶。
 *
 * <p><b>逐店查询（移植风险⑤）</b>：hotel.detail 混批下部分酒店返回 Code=0 且
 * Rooms=[] 的假空，会被误当真实无房。本实现一次只查一店。
 */
@Slf4j
@Service
public class ElongPriceServiceImpl implements ElongPriceService {

    private static final String METHOD_HOTEL_DETAIL = "hotel.detail";

    private static final String METHOD_DATA_VALIDATE = "hotel.data.validate";

    /** 验价通过的 ResultCode 原文 */
    private static final String RESULT_CODE_OK = "OK";

    @Resource
    private ElongProperties properties;

    /** 规范化与键派生的唯一权威（查价组装与 resolve 匹配共用，键与门同源） */
    @Resource
    private ElongProductKeyDeriver productKeyDeriver;

    @Resource
    private OfferStore offerStore;

    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {
        if (!properties.isConfigured()) {
            log.error("艺龙查价：凭证未配置（ELONG_USER/ELONG_APP_KEY/ELONG_SECRET），无法调用,sHotelId={}",
                    supplier.getSHotelId());
            return null;
        }
        List<String> occupancies = buildOccupancies(request.getRoomNum(), request.getAdultNum(),
                request.getChildNum(), request.getChildAges());
        request.setOccupancies(occupancies);

        ResponseResult<ElongHotelDetailResponse> result = queryHotelDetail(supplier.getSHotelId(), request.getCheckIn(),
                request.getCheckout(), request.getRoomNum(), request.getAdultNum(), request.getChildNum(), request.getChildAges());
        if (result == null || result.getData() == null) {
            log.warn("艺龙查价：调用未取得结果,sHotelId={},checkIn={}", supplier.getSHotelId(), request.getCheckIn());
            return null;
        }
        ElongHotelDetailResponse data = result.getData();
        if (!data.isSucc()) {
            // 错误码透传不归并（移植风险④）：码义未核实的一律原样落日志
            log.warn("艺龙查价：供应商返回业务错误,sHotelId={},code={}", supplier.getSHotelId(), data.getCode());
            return null;
        }
        if (data.isEmptyResult()) {
            log.info("艺龙查价：该店当日无在售产品,sHotelId={},checkIn={}", supplier.getSHotelId(), request.getCheckIn());
            return List.of();
        }
        return convertPriceResp(data.getResult().getHotels().get(0), request);
    }

    private List<ProductRespDTO> convertPriceResp(ElongHotelDetailResponse.ElongHotel hotel, PriceReq request) {
        List<ProductRespDTO> products = new ArrayList<>();
        int totalPlans = 0;
        int skippedNotOnSale = 0;
        int skippedNoCredentials = 0;
        int skippedNoPrice = 0;
        String occupancy = request.getOccupancies().get(0);
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                totalPlans++;
                if (!isOnSale(plan)) {
                    skippedNotOnSale++;
                    continue;
                }
                if (!hasSessionCredentials(plan)) {
                    skippedNoCredentials++;
                    continue;
                }
                List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
                if (dayPrices == null) {
                    skippedNoPrice++;
                    continue;
                }
                products.add(convertPlan(hotel.getHotelId(), room, plan, dayPrices, occupancy, request));
            }
        }
        // 非常态走向必须可观测（§6.2.1）：跳过的每一类都有落点，排障时能看出报价去哪了
        log.info("艺龙查价：转换完成,hotelId={},checkIn={},产品总数={},在售出报={},跳过_停售或无库存={},跳过_缺会话凭据={},跳过_缺每日价={}",
                hotel.getHotelId(), request.getCheckIn(), totalPlans, products.size(),
                skippedNotOnSale, skippedNoCredentials, skippedNoPrice);
        return products;
    }

    private ProductRespDTO convertPlan(String hotelId, ElongHotelDetailResponse.ElongRoom room, ElongRatePlan plan,
                                       List<ElongDataValidateRequest.DayPrice> dayPrices, String occupancy, PriceReq request) {
        Meal meal = productKeyDeriver.convertMeal(plan);
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
        int totalPriceCents = sumCents(dayPrices);
        ProductRespDTO product = ProductRespDTO.builder()
                .hotelId(hotelId)
                // 报价标识=GoodsUniqId（会话级易腐，申报见 SupplierIdentityProfile.ELONG）；
                // 身份=productKey，二者永不同字段
                .productId(plan.getGoodsUniqId())
                .productKey(productKeyDeriver.deriveProductKey(hotelId, plan.getRoomTypeId(), meal, cancelPolicy, occupancy))
                .supplierId(SupplierSourceEnum.ELONG.getCode())
                .room(Room.builder().roomId(plan.getRoomTypeId()).roomName(room.getName()).build())
                .productInfo(ProductInfo.builder().inventory(plan.getCurrentAlloment()).productStatus(1)
                        .productName(StringUtils.isNotBlank(plan.getRatePlanName()) ? plan.getRatePlanName() : room.getName()).build())
                .currencyType(plan.getCurrencyCode())
                .totalPrice(totalPriceCents)
                .roomTotalPrice(totalPriceCents)
                .priceInfos(buildPriceInfos(dayPrices))
                .meal(meal)
                .cancelPolicy(cancelPolicy)
                .maxOccupancy(plan.getAdultOccupancyPerRoom() != null ? plan.getAdultOccupancyPerRoom() : request.getAdultNum())
                .build();
        product.setTotalTaxes(0);
        return product;
    }

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        if (!properties.isConfigured()) {
            log.error("艺龙验价：凭证未配置（ELONG_USER/ELONG_APP_KEY/ELONG_SECRET），无法调用,sHotelId={}", request.getSHotelId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "艺龙凭证未配置，未能确认该产品是否可订");
        }
        String occupancy = buildOccupancy(request.getAdultCount(), request.getChildNum(), request.getChildAges());

        // 现取现验（R-3.1）：重打一次 hotel.detail 取本会话的新马甲与新报价码
        ResponseResult<ElongHotelDetailResponse> result = queryHotelDetail(request.getSHotelId(), request.getCheckIn(),
                request.getCheckOut(), request.getRoomNum(), request.getAdultCount(), request.getChildNum(), request.getChildAges());
        if (result == null || result.getData() == null) {
            log.warn("艺龙验价：现货查询未取得结果,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "现货查询未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        ElongHotelDetailResponse data = result.getData();
        if (!data.isSucc()) {
            log.warn("艺龙验价：现货查询返回业务错误,sHotelId={},sProductId={},code={}",
                    request.getSHotelId(), request.getSProductId(), data.getCode());
            return outcome(CheckPriceOutcome.INDETERMINATE, "现货查询失败(" + data.errorCode() + ")，未能确认该产品是否可订");
        }
        if (data.isEmptyResult()) {
            // 逐店查询下的空 Rooms 是该店当日确无在售（混批假空已由逐店纪律排除）
            log.info("艺龙验价：该店当日无在售产品,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该酒店当日已无在售产品，请重新查价");
        }

        ElongHotelDetailResponse.ElongHotel hotel = data.getResult().getHotels().get(0);
        PlanWithRoom found = findPlan(hotel, request.getSProductId());
        if (found != null && Boolean.FALSE.equals(found.plan().getStatus())) {
            log.info("艺龙验价：所点产品已停售,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已停售，请重新查价后再选择");
        }
        if (found != null && (found.plan().getCurrentAlloment() == null || found.plan().getCurrentAlloment() <= 0)) {
            // 供应商明确报库存≤0，是确定性结果
            log.info("艺龙验价：所点产品库存为0,sHotelId={},sProductId={},currentAlloment={}",
                    request.getSHotelId(), request.getSProductId(), found.plan().getCurrentAlloment());
            return outcome(CheckPriceOutcome.SOLD_OUT, "该产品已售罄");
        }
        if (found == null) {
            // 报价码已不在现货（会话级轮换是常态）：先按 productKey 换等价新票（resolve ②），
            // 换不到才是确定性 RATE_DEAD
            found = tryResolveByProductKey(hotel, request, occupancy);
        }
        if (found == null) {
            log.info("艺龙验价：所点报价码已不在现货且未能换票,sHotelId={},sProductId={},productKey={}",
                    request.getSHotelId(), request.getSProductId(), request.getProductKey());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不在供应商当前报价中，请重新查价后再选择");
        }

        ElongRatePlan plan = found.plan();
        if (!hasSessionCredentials(plan) || StringUtils.isAnyBlank(plan.getHotelCode(), plan.getSupplierId(),
                plan.getSubSupplierId(), plan.getShopperProductId())) {
            // 产品在售却缺验价必需的七项凭据，属响应自相矛盾：不能说可订，也没证据说不可订
            log.error("艺龙验价：现货产品缺验价凭据,sHotelId={},goodsUniqId={},ratePlanId={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商响应缺少验价凭据，未能确认该产品是否可订");
        }
        List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
        if (dayPrices == null) {
            log.error("艺龙验价：现货产品缺每日价,sHotelId={},goodsUniqId={}", request.getSHotelId(), plan.getGoodsUniqId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商响应缺少每日价，未能确认该产品是否可订");
        }
        return validate(request, hotel.getHotelId(), plan, dayPrices);
    }

    /** 二段：以本会话凭据打 hotel.data.validate，并把结果归入确定的分态 */
    private CheckPriceRespDTO validate(CheckPriceReq request, String hotelId, ElongRatePlan plan,
                                       List<ElongDataValidateRequest.DayPrice> dayPrices) {
        BigDecimal totalPriceYuan = sumYuan(dayPrices);
        ElongDataValidateRequest validateRequest = ElongDataValidateRequest.builder()
                .arrivalDate(request.getCheckIn())
                .departureDate(request.getCheckOut())
                // Earliest 取抓包样例常用值；Latest 固定 23:59:59——上游到店时间（如
                // amap.EarlyArrivalTime）不得透传到此，会误伤可订性判定（移植风险⑩）
                .earliestArrivalTime(request.getCheckIn() + " 12:00:00")
                .latestArrivalTime(request.getCheckIn() + " 23:59:59")
                .hotelId(hotelId)
                .hotelCode(plan.getHotelCode())
                .ratePlanId(plan.getRatePlanId())
                .roomTypeID(plan.getRoomTypeId())
                .littleMajiaId(plan.getLittleMajiaId())
                .goodsUniqId(plan.getGoodsUniqId())
                .shopperProductId(plan.getShopperProductId())
                .subSupplierId(plan.getSubSupplierId())
                .supplierId(plan.getSupplierId())
                .totalPrice(totalPriceYuan)
                .numberOfRooms(request.getRoomNum())
                .numberOfAdults(request.getAdultCount())
                .childAges(CollectionUtils.isEmpty(request.getChildAges()) ? List.of() : request.getChildAges())
                .dayPriceList(dayPrices)
                .build();
        String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), validateRequest));
        ResponseResult<ElongDataValidateResponse> result = new DataValidateAccess(properties)
                .access(new ElongRestCall(METHOD_DATA_VALIDATE, dataJson));
        if (result == null || result.getData() == null) {
            log.warn("艺龙验价：validate 调用未取得结果,sHotelId={},goodsUniqId={}", hotelId, plan.getGoodsUniqId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价调用未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        ElongDataValidateResponse data = result.getData();
        if (!data.isSucc()) {
            return classifyValidateError(request, plan, data);
        }
        String resultCode = data.getResult() == null ? null : data.getResult().getResultCode();
        if (!RESULT_CODE_OK.equalsIgnoreCase(StringUtils.trimToEmpty(resultCode))) {
            // ResultCode 非 OK 的取值（Product/Inventory/Rate）码义未经官方文档核实，
            // 只透传不归并（移植风险④）；待核实后才允许升级成 SOLD_OUT/RATE_DEAD
            log.warn("艺龙验价：ResultCode 非 OK，码义未核实按不确定处理,sHotelId={},goodsUniqId={},resultCode={},errorMessage={}",
                    hotelId, plan.getGoodsUniqId(), resultCode,
                    data.getResult() == null ? null : data.getResult().getErrorMessage());
            return outcome(CheckPriceOutcome.INDETERMINATE,
                    "验价未通过(ResultCode=" + resultCode + ")，未能确认该产品是否可订");
        }
        return buildBookableResp(request, hotelId, plan, dayPrices, totalPriceYuan, data);
    }

    /**
     * 验价业务错误码 → 三态。纪律：只有确证不因重试而改变的才判确定态；产品级死码必须
     * RATE_DEAD，绝不折叠进"不确定"——cursor 把 H001083 兜成可订导致建单暴死丢真单。
     */
    private CheckPriceRespDTO classifyValidateError(CheckPriceReq request, ElongRatePlan plan,
                                                    ElongDataValidateResponse data) {
        String errorCode = StringUtils.trimToEmpty(data.errorCode());
        String full = data.getCode() + "|" + (data.getResult() == null ? "" : StringUtils.trimToEmpty(data.getResult().getErrorMessage()));
        if (errorCode.startsWith("H001083")) {
            // 内层 7015=餐食变化、7010=国际产品不可订：对本次所点报价而言都意味着该票已死，
            // 上游重新查价即可拿到换代后的报价。区分二者只为排障方向（§6.2.2）
            String innerCause = full.contains("7015") ? "7015餐食变化" : (full.contains("7010") ? "7010产品不可订" : "未携内层码");
            log.info("艺龙验价：产品级死码,sHotelId={},goodsUniqId={},errorCode={},内层={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode, innerCause);
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不可订(" + errorCode + ")，请重新查价后再选择");
        }
        if (errorCode.startsWith("H001144")) {
            // 马甲过期本可现取现验救回，但本次马甲就是刚取的——即刻过期属链路异常，重试可能成功
            log.warn("艺龙验价：本会话刚取的马甲被报过期,sHotelId={},goodsUniqId={},errorCode={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode);
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价凭证异常(H001144)，未能确认该产品是否可订，请稍后重试");
        }
        if (errorCode.startsWith("H001197") || errorCode.startsWith("H001188")) {
            // 缺马甲/每日价字段错，均属我方请求组装缺陷，需人工介入
            log.error("艺龙验价：请求组装缺陷被供应商拒绝,sHotelId={},goodsUniqId={},errorCode={},response={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode, result(data));
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价请求异常(" + errorCode + ")，未能确认该产品是否可订");
        }
        // 其余（含 H001084/H001189 等码义未核实的）一律透传不归并（移植风险④）
        log.warn("艺龙验价：未核实错误码，按不确定处理,sHotelId={},goodsUniqId={},code={}",
                request.getSHotelId(), plan.getGoodsUniqId(), data.getCode());
        return outcome(CheckPriceOutcome.INDETERMINATE, "验价未通过(" + errorCode + ")，未能确认该产品是否可订");
    }

    /**
     * 验价通过 → 签发句柄并回报 BOOKABLE。
     *
     * <p>价格以验价响应 interValidateInfo.ratePlanInfo.RateNightlyRateList 为准
     * （供应商验后价）；未下发或与住期对不齐时回落本会话查价合计。签发不成即不可报可订
     * （报得出价却下不了单，比验价失败更糟）。
     */
    private CheckPriceRespDTO buildBookableResp(CheckPriceReq request, String hotelId, ElongRatePlan plan,
                                                List<ElongDataValidateRequest.DayPrice> dayPrices,
                                                BigDecimal totalPriceYuan, ElongDataValidateResponse data) {
        int salePriceCents = validatedPriceCents(data, dayPrices.size())
                .orElse(totalPriceYuan.multiply(BigDecimal.valueOf(100)).intValue());
        Map<String, String> credentials = new HashMap<>();
        credentials.put(ElongOfferCredentials.HOTEL_ID, hotelId);
        credentials.put(ElongOfferCredentials.HOTEL_CODE, plan.getHotelCode());
        credentials.put(ElongOfferCredentials.ROOM_TYPE_ID, plan.getRoomTypeId());
        credentials.put(ElongOfferCredentials.RATE_PLAN_ID, String.valueOf(plan.getRatePlanId()));
        credentials.put(ElongOfferCredentials.GOODS_UNIQ_ID, plan.getGoodsUniqId());
        credentials.put(ElongOfferCredentials.LITTLE_MAJIA_ID, plan.getLittleMajiaId());
        credentials.put(ElongOfferCredentials.SUPPLIER_ID, plan.getSupplierId());
        credentials.put(ElongOfferCredentials.SUB_SUPPLIER_ID, plan.getSubSupplierId());
        credentials.put(ElongOfferCredentials.SHOPPER_PRODUCT_ID, plan.getShopperProductId());
        credentials.put(ElongOfferCredentials.TOTAL_PRICE, totalPriceYuan.toPlainString());
        credentials.put(ElongOfferCredentials.DAY_PRICE_LIST, JsonUtils.writeObject2Json(dayPrices));
        String offerId = offerStore.issue(SupplierSourceEnum.ELONG.getCode(), credentials);
        if (StringUtils.isBlank(offerId)) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        log.info("艺龙验价：通过并签发句柄,sHotelId={},goodsUniqId={},salePrice={}分,offerId={}",
                hotelId, plan.getGoodsUniqId(), salePriceCents, offerId);
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.getTtlSeconds())
                .salePrice(salePriceCents)
                .subPrice(salePriceCents)
                .remainRoomNum(restInventory(data))
                .build();
    }

    /**
     * 令牌已死时按 productKey 在本会话现货中找等价新票（resolve ②，docs/product-identity.md §3）。
     *
     * <p>三个前置缺一即放弃（走 RATE_DEAD 正门）：开关 supplier.elong.resolve-enabled
     * 开启（默认关）、上游携带 productKey、上游携带展示价 totalPrice（容差门基准，R-3.3）。
     * 硬门（R-3.2）由键相等保证：对每条现货按与查价<b>完全相同的口径</b>重新派生再比对。
     * 匹配在已取回的现货响应上进行，不追加供应商调用（R-3.4）。
     */
    PlanWithRoom tryResolveByProductKey(ElongHotelDetailResponse.ElongHotel hotel, CheckPriceReq request, String occupancy) {
        if (StringUtils.isBlank(request.getProductKey())) {
            return null;
        }
        if (!properties.isResolveEnabled()) {
            // §3.8.4：上游明确请求了换票（带 productKey）而被闸口拒绝，必须可检索
            log.info("闸口 supplier.elong.resolve-enabled 关闭，拒绝按 productKey 自动换票,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return null;
        }
        List<ResolveCandidate> equivalents = new ArrayList<>();
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                if (!isOnSale(plan) || !hasSessionCredentials(plan)) {
                    continue;
                }
                List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
                if (dayPrices == null) {
                    continue;
                }
                Meal meal = productKeyDeriver.convertMeal(plan);
                List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
                String key = productKeyDeriver.deriveProductKey(hotel.getHotelId(), plan.getRoomTypeId(), meal, cancelPolicy, occupancy);
                if (!request.getProductKey().equals(key)) {
                    continue;
                }
                equivalents.add(new ResolveCandidate(new PlanWithRoom(room, plan), sumCents(dayPrices)));
            }
        }
        return ResolveGate.pickCheapestWithinTolerance(equivalents, ResolveCandidate::priceCents,
                        request.getTotalPrice(), properties.getResolvePriceTolerance())
                .map(chosen -> {
                    log.info("艺龙验价：令牌已死，按productKey换票成功,原sProductId={},新goodsUniqId={},新价={}分,展示价={}分",
                            request.getSProductId(), chosen.planWithRoom().plan().getGoodsUniqId(),
                            chosen.priceCents(), request.getTotalPrice());
                    return chosen.planWithRoom();
                })
                .orElseGet(() -> {
                    // 未救回的两种成因必须可区分（§6.2.2）："没等价票"查建档/键口径，"价格不合"查容差参数
                    if (equivalents.isEmpty()) {
                        log.info("艺龙验价：resolve 未命中——现货中无同卖法等价报价,sHotelId={},sProductId={},productKey={}",
                                request.getSHotelId(), request.getSProductId(), request.getProductKey());
                    } else {
                        log.info("艺龙验价：存在等价报价但超出容差，拒绝自动换票,sProductId={},展示价={}分,候选最低={}分",
                                request.getSProductId(), request.getTotalPrice(),
                                equivalents.stream().mapToInt(ResolveCandidate::priceCents).min().orElse(-1));
                    }
                    return null;
                });
    }

    /** 逐店 hotel.detail（SaveMajiaId=true 取本会话马甲），查价与验价共用同一起手式 */
    private ResponseResult<ElongHotelDetailResponse> queryHotelDetail(String hotelId, String checkIn, String checkOut,
                                                                      Integer roomNum, Integer adultNum,
                                                                      Integer childNum, List<Integer> childAges) {
        ElongHotelDetailRequest detailRequest = ElongHotelDetailRequest.builder()
                .arrivalDate(checkIn)
                .departureDate(checkOut)
                .hotelIds(hotelId)
                .numberOfAdults(adultNum)
                .numberOfRooms(roomNum)
                .childAges(childNum != null && childNum > 0 && CollectionUtils.isNotEmpty(childAges) ? childAges : List.of())
                .build();
        String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), detailRequest));
        return new HotelDetailAccess(properties).access(new ElongRestCall(METHOD_HOTEL_DETAIL, dataJson));
    }

    /** 在现货中按报价码（GoodsUniqId）找所点产品；找不到返回 null */
    private PlanWithRoom findPlan(ElongHotelDetailResponse.ElongHotel hotel, String goodsUniqId) {
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                if (goodsUniqId != null && goodsUniqId.equals(plan.getGoodsUniqId())) {
                    return new PlanWithRoom(room, plan);
                }
            }
        }
        return null;
    }

    /** 在售判定：总开关开 ∧ 库存>0 ∧ 有房型锚（键成分，缺了无从派生身份） */
    private static boolean isOnSale(ElongRatePlan plan) {
        return !Boolean.FALSE.equals(plan.getStatus())
                && plan.getCurrentAlloment() != null && plan.getCurrentAlloment() > 0
                && StringUtils.isNotBlank(plan.getRoomTypeId());
    }

    /** 会话凭据（马甲+报价码）齐备才可出报——报出来也验不了的价是给上游埋雷 */
    private static boolean hasSessionCredentials(ElongRatePlan plan) {
        return StringUtils.isNotBlank(plan.getGoodsUniqId()) && StringUtils.isNotBlank(plan.getLittleMajiaId());
    }

    /**
     * 每日价：validate 的 DayPriceList 与查价报出的总价同源（Member 售价 / MinRate）。
     * 任一晚缺 Member 或 MinRate 即整体不可用（validate 必传，缺了必被拒）。
     */
    private static List<ElongDataValidateRequest.DayPrice> buildDayPrices(List<ElongNightlyRate> nightlyRates) {
        if (CollectionUtils.isEmpty(nightlyRates)) {
            return null;
        }
        List<ElongDataValidateRequest.DayPrice> dayPrices = new ArrayList<>();
        for (ElongNightlyRate nightly : nightlyRates) {
            if (nightly.getMember() == null || nightly.getMinRate() == null
                    || StringUtils.isBlank(nightly.getDate()) || nightly.getDate().length() < 10) {
                return null;
            }
            dayPrices.add(ElongDataValidateRequest.DayPrice.builder()
                    .date(nightly.getDate().substring(0, 10))
                    .price(nightly.getMember())
                    .minRate(nightly.getMinRate())
                    .build());
        }
        return dayPrices;
    }

    private static List<PriceInfo> buildPriceInfos(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        List<PriceInfo> priceInfos = new ArrayList<>();
        for (ElongDataValidateRequest.DayPrice dayPrice : dayPrices) {
            int cents = dayPrice.getPrice().multiply(BigDecimal.valueOf(100)).intValue();
            priceInfos.add(PriceInfo.builder().date(dayPrice.getDate()).price(cents).roomPrice(cents).taxes(0).build());
        }
        return priceInfos;
    }

    private static BigDecimal sumYuan(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ElongDataValidateRequest.DayPrice dayPrice : dayPrices) {
            sum = sum.add(dayPrice.getPrice());
        }
        return sum;
    }

    private static int sumCents(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        return sumYuan(dayPrices).multiply(BigDecimal.valueOf(100)).intValue();
    }

    /** 验后价：interValidateInfo.ratePlanInfo.RateNightlyRateList 逐日 Rate 求和（元→分），与住期对不齐即放弃 */
    private static Optional<Integer> validatedPriceCents(ElongDataValidateResponse data, int nights) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        JsonNode list = info == null ? null : info.path("ratePlanInfo").path("RateNightlyRateList");
        if (list == null || !list.isArray() || list.size() != nights) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode night : list) {
            if (!night.hasNonNull("Rate")) {
                return Optional.empty();
            }
            sum = sum.add(night.get("Rate").decimalValue());
        }
        return Optional.of(sum.multiply(BigDecimal.valueOf(100)).intValue());
    }

    private static Integer restInventory(ElongDataValidateResponse data) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        JsonNode rest = info == null ? null : info.path("ratePlanInfo").path("RestInventoryCount");
        return rest != null && rest.canConvertToInt() ? rest.asInt() : null;
    }

    /** 占用串与 Expedia 同口径：一间房一项，「成人数-儿童年龄,儿童年龄」 */
    private static List<String> buildOccupancies(Integer roomNum, Integer adultNum, Integer childNum, List<Integer> childAges) {
        List<String> occupancies = new ArrayList<>();
        for (int i = 0; i < roomNum; i++) {
            occupancies.add(buildOccupancy(adultNum, childNum, childAges));
        }
        return occupancies;
    }

    private static String buildOccupancy(Integer adultNum, Integer childNum, List<Integer> childAges) {
        StringBuilder occupancy = new StringBuilder(String.valueOf(adultNum));
        if (childNum != null && childNum > 0 && CollectionUtils.isNotEmpty(childAges)) {
            for (int i = 0; i < childAges.size(); i++) {
                occupancy.append(i == 0 ? "-" : ",").append(childAges.get(i));
            }
        }
        return occupancy.toString();
    }

    private static String result(ElongDataValidateResponse data) {
        return JsonUtils.writeObject2Json(data);
    }

    private static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private CheckPriceRespDTO outcome(CheckPriceOutcome outcome, String message) {
        return CheckPriceRespDTO.builder().outcome(outcome).message(message).build();
    }

    /** 现货产品及其所在物理房型（房名展示用） */
    record PlanWithRoom(ElongHotelDetailResponse.ElongRoom room, ElongRatePlan plan) {
    }

    /** resolve 候选：已过硬门（productKey 相等）的现货报价及其上游口径价格（分） */
    private record ResolveCandidate(PlanWithRoom planWithRoom, int priceCents) {
    }
}
