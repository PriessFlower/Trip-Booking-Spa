package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

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
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.checkprice.client.PriceConfirmAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing.client.PriceSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRequestHeader;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 道旅查价/验价协议逻辑。依据只认官方文档 {@code apidoc.didatravel.com/zh}
 * （booking-api/price-search、booking-api/price-confirm、information-hub/api-error-code，
 * 均 2026-09-08 查阅）；文档没写而实测得到的，在各处注明「实测」与日期。
 *
 * <p><b>逐店查询</b>：多店 + {@code IsRealTime=true} 会被道旅降级（2026-09-08 实测：逐店实时
 * 280 条 vs 10 家一批实时 105 条，4 家整家消失），判据与三种组合的对照见
 * {@link com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchRequest}。
 *
 * <p><b>现取现验（R-3.1）</b>：验价第一步永远是重打一次 pricesearch。道旅 RatePlanID 腐得
 * 极快——同参数间隔 3 秒重查，所点报价码已不在响应中（2026-09-08 实测）。
 */
@Slf4j
@Service
public class DidaPriceServiceImpl implements DidaPriceService {

    /** 报价失效族：这些码说的都是「所点报价没了」，上游重新查价即可拿到换代后的报价 */
    private static final Set<String> RATE_DEAD_CODES = Set.of(
            "2006",   // 此价格计划失效
            "2020",   // RatePlanID不正确
            "2029",   // 酒店停止售卖
            "2030");  // 价格不可用

    /** 无库存：供应商明确回答满房 */
    private static final String SOLD_OUT_CODE = "2005";

    @Resource
    private DidaProperties properties;

    /** 规范化与键派生的唯一权威（查价组装与 resolve 匹配共用，键与门同源） */
    @Resource
    private DidaProductKeyDeriver productKeyDeriver;

    @Resource
    private OfferStore offerStore;

    @Override
    public PricingResult queryPrices(PriceReq request, Supplier supplier, CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("道旅查价：凭证未配置（DIDA_CLIENT_ID/DIDA_LICENSE_KEY），无法调用,sHotelId={}",
                    supplier.getSHotelId());
            return PricingResult.indeterminate();
        }
        Long hotelId = hotelIdOf(supplier.getSHotelId());
        if (hotelId == null) {
            log.error("道旅查价：酒店 id 非数字，无法调用,sHotelId={}", supplier.getSHotelId());
            return PricingResult.indeterminate();
        }
        request.setOccupancies(Occupancy.perRoom(request.getRoomNum(), request.getAdultNum(),
                request.getChildNum(), request.getChildAges()));

        DidaPriceSearchResponse data = search(hotelId, request.getCheckIn(), request.getCheckout(),
                request.getRoomNum(), request.getAdultNum(), request.getChildNum(), request.getChildAges(), purpose);
        if (data == null) {
            log.warn("道旅查价：调用未取得结果,sHotelId={},checkIn={}", supplier.getSHotelId(), request.getCheckIn());
            return PricingResult.indeterminate();
        }
        return toPricingResult(data, request, supplier.getSHotelId());
    }

    /**
     * 现货响应 → 分态+产品。查价与验价即刷回写共用这一段——两条路对「无货 / 未能确认 / 在售」
     * 的口径必须同源，回写另起口径会把 F-5.1（失败不动缓存）或僵尸价清理（B7）弄丢一头。
     */
    PricingResult toPricingResult(DidaPriceSearchResponse data, PriceReq request, String sHotelId) {
        if (!data.isSucc()) {
            String code = StringUtils.trimToEmpty(data.errorCode());
            if (SOLD_OUT_CODE.equals(code) || RATE_DEAD_CODES.contains(code)) {
                // 供应商明确说没得卖（满房 / 该价格计划失效 / 酒店停售）——这是确定无货，
                // 缓存该被清（B7 僵尸价），不是"没问出来"
                log.info("道旅查价：供应商明确无可售,sHotelId={},checkIn={},code={},message={}",
                        sHotelId, request.getCheckIn(), code, data.errorMessage());
                return PricingResult.noInventory();
            }
            // 码义未核实的一律原样落日志、按未能确认回报（不归并）
            log.warn("道旅查价：供应商返回业务错误,sHotelId={},checkIn={},code={},message={}",
                    sHotelId, request.getCheckIn(), code, data.errorMessage());
            return PricingResult.indeterminate();
        }
        if (data.isEmptyResult()) {
            log.info("道旅查价：该店当日无在售产品,sHotelId={},checkIn={}", sHotelId, request.getCheckIn());
            return PricingResult.noInventory();
        }
        DidaHotel hotel = data.firstHotel();
        if (CollectionUtils.isEmpty(hotel.getRatePlanList())) {
            // 酒店回来了、一条报价都没给：确定无货（与 isEmptyResult 是两种形态，都要落无货标记）
            log.info("道旅查价：该店当日无任何报价,sHotelId={},checkIn={}", sHotelId, request.getCheckIn());
            return PricingResult.noInventory();
        }
        List<ProductRespDTO> products = convertPriceResp(hotel, request);
        if (products.isEmpty()) {
            // 供应商给了报价、被我方过滤全丢（非即时确认 / 缺逐晚价 / 价为零）。这里不能说
            // NO_INVENTORY：房其实还在，说成无房会让上游据此劝退旅客
            log.info("道旅查价：供应商给了报价但被我方过滤全丢，按未能确认回报,sHotelId={},checkIn={}",
                    sHotelId, request.getCheckIn());
            return PricingResult.indeterminate();
        }
        return PricingResult.available(products);
    }

    private List<ProductRespDTO> convertPriceResp(DidaHotel hotel, PriceReq request) {
        List<ProductRespDTO> products = new ArrayList<>();
        int totalPlans = 0;
        int skippedOnRequest = 0;
        int skippedNoDayPrice = 0;
        int skippedZeroPrice = 0;
        String occupancy = request.getOccupancies().get(0);
        int nights = nightsOf(request.getCheckIn(), request.getCheckout());
        String hotelId = String.valueOf(hotel.getHotelId());
        for (DidaRatePlan plan : hotel.getRatePlanList()) {
            totalPlans++;
            if (plan.onRequest()) {
                skippedOnRequest++;
                continue;
            }
            if (!hasFullDayPrices(plan, nights)) {
                skippedNoDayPrice++;
                continue;
            }
            Integer totalCents = totalCentsOf(plan);
            if (totalCents == null || totalCents <= 0) {
                skippedZeroPrice++;
                continue;
            }
            products.add(convertPlan(hotelId, plan, totalCents, occupancy, request));
        }
        // 非常态走向必须可观测（§6.2.1）：跳过的每一类都有落点。日志答「这一家这一天」，
        // 指标答「整体丢了多少」（O-1.3）
        log.info("道旅查价：转换完成,hotelId={},checkIn={},产品总数={},在售出报={},跳过_非即时确认={},"
                        + "跳过_缺逐晚价={},跳过_价为零={}",
                hotelId, request.getCheckIn(), totalPlans, products.size(),
                skippedOnRequest, skippedNoDayPrice, skippedZeroPrice);
        countConvertDropped(DropReason.ON_REQUEST, skippedOnRequest);
        countConvertDropped(DropReason.NO_DAY_PRICE, skippedNoDayPrice);
        countConvertDropped(DropReason.ZERO_TOTAL_PRICE, skippedZeroPrice);
        return products;
    }

    private static void countConvertDropped(DropReason reason, int count) {
        if (count > 0) {
            Monitor.recordMany(MetricNames.QUOTE_DROPPED,
                    MetricTags.dropped(SupplierSourceEnum.DIDA, FunnelStage.CONVERT, reason), count);
        }
    }

    private ProductRespDTO convertPlan(String hotelId, DidaRatePlan plan, int totalCents,
                                       String occupancy, PriceReq request) {
        Meal meal = productKeyDeriver.convertMeal(plan);
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.getCheckIn(), plan.getRatePlanCancellationPolicyList());
        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
        ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelId, roomTypeIdOf(plan),
                meal, cancelPolicy, occupancy, totalCents);
        ProductRespDTO product = ProductRespDTO.builder()
                .hotelId(hotelId)
                // 报价标识=RatePlanID（易腐，实测 3 秒即换代）；身份=productKey，二者永不同字段
                .productId(plan.getRatePlanId())
                .productKey(identity.productKey())
                .identity(identity)
                .supplierId(SupplierSourceEnum.DIDA.getCode())
                .room(Room.builder().roomId(roomTypeIdOf(plan)).roomName(roomNameOf(plan)).build())
                // inventory 原样透出 InventoryCount。官方字段说明写着「仅供参考，不准的。供应商
                // 自身就无法提供准确的数字」——上游据此过滤会误杀，故只透传不解释
                .productInfo(ProductInfo.builder()
                        .inventory(plan.getInventoryCount())
                        .productStatus(1)
                        .productName(StringUtils.isNotBlank(plan.getRatePlanName())
                                ? plan.getRatePlanName() : roomNameOf(plan))
                        .build())
                .currencyType(StringUtils.defaultIfBlank(plan.getCurrency(), properties.getCurrency()))
                // pricesearch 的 TotalPrice 是<b>单间</b>住期总价（官方 price-search：搜 3 间需自行 ×3）；
                // 缓存与出价都按单间口径存，多间由验价/下单侧乘间数
                .totalPrice(totalCents)
                .priceInfos(buildPriceInfos(plan.getPriceList()))
                .meal(meal)
                .cancelPolicy(cancelPolicy)
                .build();
        // 税费：IncludedFeeList 已包含在 TotalPrice 内（官方明示不要再做运算），故此处只报 0，
        // 不把它当成"额外税费"加减
        product.setTotalTaxes(0);
        return product;
    }

    // ---------- 验价钩子：流程在 DidaCheckPriceServiceImpl（模板），这里只是供应商侧的读法 ----------

    /** 凭证未配置即确定失败（网关无兜底），不调供应商 */
    public CheckPriceRespDTO precondition(CheckPriceReq request) {
        if (!properties.isConfigured()) {
            log.error("道旅验价：凭证未配置（DIDA_CLIENT_ID/DIDA_LICENSE_KEY），无法调用,sHotelId={}",
                    request.getSHotelId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "道旅凭证未配置，未能确认该产品是否可订");
        }
        return null;
    }

    /**
     * 验价即刷的转换（机制在 {@code AbstractCheckPriceFlow}）：分态与转换复用
     * {@link #toPricingResult}——异常价拦截、TTL 分档、无货落缓存全部继承刷价写路径。
     * INDETERMINATE → null = 不动缓存（F-5.1）。
     */
    public List<ProductRespDTO> freshProducts(DidaPriceSearchResponse data, PriceReq priceReq, String sHotelId) {
        PricingResult classified = toPricingResult(data, priceReq, sHotelId);
        return classified.outcome() == PricingOutcome.INDETERMINATE ? null : classified.products();
    }

    /** 现取现验（R-3.1）：重打一次 pricesearch 取本次的新报价码 */
    public LiveStock<DidaHotel> fetchLiveStock(CheckPriceReq request) {
        Long hotelId = hotelIdOf(request.getSHotelId());
        if (hotelId == null) {
            log.error("道旅验价：酒店 id 非数字,sHotelId={}", request.getSHotelId());
            return LiveStock.terminal(outcome(CheckPriceOutcome.INDETERMINATE, "酒店标识非法，未能确认该产品是否可订"));
        }
        DidaPriceSearchResponse data = search(hotelId, request.getCheckIn(), request.getCheckOut(),
                request.getRoomNum(), request.getAdultCount(), request.getChildNum(), request.getChildAges(),
                CallPurpose.CHECK_PRICE);
        if (data == null) {
            log.warn("道旅验价：现货查询未取得结果,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return LiveStock.terminal(outcome(CheckPriceOutcome.INDETERMINATE,
                    "现货查询未取得结果，未能确认该产品是否可订，请稍后重试"));
        }
        // 验价即刷的转换器：闭包捕获这份原始响应，终态分支也带着它返回——
        // 下架/整店无售正是要落无货标记的时候
        Function<PriceReq, List<ProductRespDTO>> fresh =
                priceReq -> freshProducts(data, priceReq, request.getSHotelId());
        if (!data.isSucc()) {
            String code = StringUtils.trimToEmpty(data.errorCode());
            CheckPriceOutcome terminal = SOLD_OUT_CODE.equals(code) ? CheckPriceOutcome.SOLD_OUT
                    : RATE_DEAD_CODES.contains(code) ? CheckPriceOutcome.RATE_DEAD
                            : CheckPriceOutcome.INDETERMINATE;
            log.info("道旅验价：现货查询返回业务错误,sHotelId={},sProductId={},code={},message={},判定={}",
                    request.getSHotelId(), request.getSProductId(), code, data.errorMessage(), terminal);
            return LiveStock.<DidaHotel>terminal(outcome(terminal,
                    "现货查询失败(" + code + ")，" + terminalMessage(terminal))).freshConvertedBy(fresh);
        }
        DidaHotel hotel = data.firstHotel();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getRatePlanList())) {
            log.info("道旅验价：该店当日无在售产品,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return LiveStock.<DidaHotel>terminal(outcome(CheckPriceOutcome.RATE_DEAD,
                    "该酒店当日已无在售产品，请重新查价")).freshConvertedBy(fresh);
        }
        return LiveStock.of(hotel).freshConvertedBy(fresh);
    }

    /** 按上游回传的报价码精确找票；非即时确认的报价不算票（本批不卖） */
    public DidaRatePlan findPlan(DidaHotel hotel, String ratePlanId) {
        if (StringUtils.isBlank(ratePlanId)) {
            return null;
        }
        for (DidaRatePlan plan : hotel.getRatePlanList()) {
            if (!plan.onRequest() && ratePlanId.equals(plan.getRatePlanId())) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 换票候选：现货里可成交、有完整逐晚价的报价，按与查价<b>完全相同</b>的口径重派生
     * productKey，键相等才收；价格取单间住期总价，与查价透出的 totalPrice 同口径。
     */
    public List<ResolveCandidate<DidaRatePlan>> resolveCandidates(DidaHotel hotel, CheckPriceReq request) {
        String occupancy = Occupancy.canonical(request.getAdultCount(), request.getChildNum(), request.getChildAges());
        int nights = nightsOf(request.getCheckIn(), request.getCheckOut());
        String hotelId = String.valueOf(hotel.getHotelId());
        List<ResolveCandidate<DidaRatePlan>> equivalents = new ArrayList<>();
        for (DidaRatePlan plan : hotel.getRatePlanList()) {
            if (plan.onRequest() || !hasFullDayPrices(plan, nights)) {
                continue;
            }
            Integer totalCents = totalCentsOf(plan);
            if (totalCents == null || totalCents <= 0) {
                continue;
            }
            Meal meal = productKeyDeriver.convertMeal(plan);
            List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                    request.getCheckIn(), plan.getRatePlanCancellationPolicyList());
            String key = productKeyDeriver.deriveProductKey(hotelId, roomTypeIdOf(plan), meal,
                    cancelPolicy, occupancy, totalCents);
            if (!key.equals(request.getProductKey())) {
                continue;
            }
            equivalents.add(new ResolveCandidate<>(plan, totalCents));
        }
        return equivalents;
    }

    /** 找到票之后的自检：现货里的票已排除停售与非即时确认，此处只挡缺逐晚价 */
    public CheckPriceRespDTO inspect(DidaRatePlan plan, CheckPriceReq request) {
        if (!hasFullDayPrices(plan, nightsOf(request.getCheckIn(), request.getCheckOut()))) {
            log.info("道旅验价：所点产品缺逐晚价,sHotelId={},ratePlanId={}",
                    request.getSHotelId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出逐晚价，未能确认该产品是否可订");
        }
        return null;
    }

    /**
     * 曝光档的应答：只报「还在售」，不报「可订」。
     *
     * <p>三条纪律：<b>offerId 恒为 null</b>（没打验价就没有"此刻可成单"的证据，且道旅报价码
     * 几秒即换代，签出去只会诱导上游拿必死的凭据建单）；退改取查价时点的
     * {@code RatePlanCancellationPolicyList}，解析不出即空、不猜（R-5.4）；
     * <b>remainRoomNum 恒为 null</b>——道旅官方明说 InventoryCount「不准」，把不准的数报成
     * 剩余房量等于把猜测说成事实。
     */
    public CheckPriceRespDTO availabilityOnlyResp(CheckPriceReq request, DidaRatePlan plan) {
        Integer perRoomCents = totalCentsOf(plan);
        if (perRoomCents == null || perRoomCents <= 0) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出价格，未能确认该产品");
        }
        int totalCents = perRoomCents * roomsOf(request);
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.getCheckIn(), plan.getRatePlanCancellationPolicyList());
        log.info("道旅验价(仅现货)：有货但未验证可订性,sHotelId={},ratePlanId={},价格={}分,退改条数={}",
                request.getSHotelId(), plan.getRatePlanId(), totalCents, cancelPolicy.size());
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.AVAILABLE)
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(StringUtils.defaultIfBlank(plan.getCurrency(), properties.getCurrency()))
                .cancelPolicy(cancelPolicy)
                .priceInfos(buildPriceInfos(plan.getPriceList()))
                .build();
    }

    /** 下单前档：打 PriceConfirm(PreBook=true) 取 ReferenceNo，并把结果归入确定的分态 */
    public CheckPriceRespDTO validate(CheckPriceReq request, DidaHotel hotel, DidaRatePlan plan) {
        DidaPriceConfirmRequest confirmRequest = buildConfirmRequest(request, hotel, plan);
        ResponseResult<DidaPriceConfirmResponse> result =
                new PriceConfirmAccess(properties).access(confirmRequest, CallPurpose.CHECK_PRICE);
        DidaPriceConfirmResponse data = result == null ? null : result.getData();
        if (data == null) {
            log.warn("道旅验价：PriceConfirm 调用未取得结果,sHotelId={},ratePlanId={}",
                    request.getSHotelId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价调用未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        return interpretConfirmResponse(request, hotel, plan, data);
    }

    /**
     * 验价响应 → 终态。与 HTTP 调用分开，是为了让「哪个码落哪一态」能被单测直接驱动——
     * 这段判定是本次接入里最容易出资损的地方（architecture.md §5 第四步）。
     */
    CheckPriceRespDTO interpretConfirmResponse(CheckPriceReq request, DidaHotel hotel, DidaRatePlan plan,
                                               DidaPriceConfirmResponse data) {
        if (!data.isSucc()) {
            return classifyConfirmError(request, plan, data);
        }
        DidaRatePlan confirmed = data.firstRatePlan();
        if (confirmed == null) {
            // 验价成功却没带回这条报价：该票已死，上游重新查价即可拿到换代后的报价
            log.info("道旅验价：响应未回传所点报价，判为死票,sHotelId={},ratePlanId={}",
                    request.getSHotelId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不可订，请重新查价后再选择");
        }
        String referenceNo = data.referenceNo();
        if (StringUtils.isBlank(referenceNo)) {
            // PreBook=true 必回 ReferenceNo（官方 price-confirm）。没有它就下不了单，
            // 报"可订"等于把不确定说成确定
            log.error("道旅验价：PreBook 未回 ReferenceNo，无法下单,sHotelId={},ratePlanId={}",
                    request.getSHotelId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价未取得下单参考号，未能确认该产品是否可订");
        }
        return buildBookableResp(request, hotel, plan, confirmed, data, referenceNo);
    }

    /**
     * 验价业务错误码 → 三态（官方 information-hub/api-error-code，2026-09-08 查阅）。
     * 纪律：只有确证不因重试而改变的才判确定态；产品级死码必须 RATE_DEAD，绝不折叠进"不确定"。
     */
    private CheckPriceRespDTO classifyConfirmError(CheckPriceReq request, DidaRatePlan plan,
                                                   DidaPriceConfirmResponse data) {
        String code = StringUtils.trimToEmpty(data.errorCode());
        if (SOLD_OUT_CODE.equals(code)) {
            log.info("道旅验价：供应商明确无库存,sHotelId={},ratePlanId={},code={}",
                    request.getSHotelId(), plan.getRatePlanId(), code);
            return outcome(CheckPriceOutcome.SOLD_OUT, "该产品已售罄");
        }
        if (RATE_DEAD_CODES.contains(code)) {
            log.info("道旅验价：产品级死码,sHotelId={},ratePlanId={},code={},message={}",
                    request.getSHotelId(), plan.getRatePlanId(), code, data.errorMessage());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不可订(" + code + ")，请重新查价后再选择");
        }
        if ("2017".equals(code) || "2019".equals(code)) {
            // 机构信息验证失败 / 请求被禁止：凭据或白名单出了问题，须人工介入
            log.error("道旅验价：账号或权限异常,sHotelId={},ratePlanId={},code={},message={}",
                    request.getSHotelId(), plan.getRatePlanId(), code, data.errorMessage());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价被供应商拒绝(" + code + ")，未能确认该产品是否可订");
        }
        // 其余码义未核实的一律透传不归并：参数类多半是我方组装缺陷，系统类重试可能好转，
        // 两者都不足以断言这条报价的死活
        log.warn("道旅验价：未归类错误码，按不确定处理,sHotelId={},ratePlanId={},code={},message={}",
                request.getSHotelId(), plan.getRatePlanId(), code, data.errorMessage());
        return outcome(CheckPriceOutcome.INDETERMINATE, "验价未通过(" + code + ")，未能确认该产品是否可订");
    }

    /**
     * 验价通过 → 签发句柄并回报 BOOKABLE。
     *
     * <p>价格以 PriceConfirm 回传的为准（供应商验后价）：官方 price-confirm 写明它这一档的
     * {@code TotalPrice} 是<b>全部房间</b>的总价，故 salePrice 直接取它，不再乘间数。
     * 逐晚明细取验价响应的 {@code PriceList}（单间口径，与查价同形）。
     */
    private CheckPriceRespDTO buildBookableResp(CheckPriceReq request, DidaHotel hotel, DidaRatePlan searched,
                                                DidaRatePlan confirmed, DidaPriceConfirmResponse data,
                                                String referenceNo) {
        Integer salePriceCents = totalCentsOf(confirmed);
        if (salePriceCents == null || salePriceCents <= 0) {
            log.error("道旅验价：验后价缺失,sHotelId={},ratePlanId={}", request.getSHotelId(), searched.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价未回价格，未能确认该产品是否可订");
        }
        String currency = StringUtils.defaultIfBlank(confirmed.getCurrency(), properties.getCurrency());
        Map<String, String> credentials = new HashMap<>();
        credentials.put(DidaOfferCredentials.REFERENCE_NO, referenceNo);
        credentials.put(DidaOfferCredentials.HOTEL_ID, String.valueOf(hotel.getHotelId()));
        credentials.put(DidaOfferCredentials.RATE_PLAN_ID, confirmed.getRatePlanId());
        credentials.put(DidaOfferCredentials.CHECK_IN, request.getCheckIn());
        credentials.put(DidaOfferCredentials.CHECK_OUT, request.getCheckOut());
        credentials.put(DidaOfferCredentials.ROOM_NUM, String.valueOf(roomsOf(request)));
        credentials.put(DidaOfferCredentials.ADULT_COUNT,
                String.valueOf(request.getAdultCount() == null ? 1 : request.getAdultCount()));
        credentials.put(DidaOfferCredentials.CHILD_AGES, childAgesCsv(request.getChildAges()));
        credentials.put(DidaOfferCredentials.DECLARED_TOTAL,
                confirmed.getTotalPrice() == null ? "" : confirmed.getTotalPrice().toPlainString());
        credentials.put(DidaOfferCredentials.CURRENCY, currency);
        credentials.put(DidaOfferCredentials.NATIONALITY, properties.getNationality());
        String offerId = offerStore.issue(SupplierSourceEnum.DIDA.getCode(), credentials);
        if (StringUtils.isBlank(offerId)) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        // 退改以验价时点为准（查价与验价之间条款可能已变，下单认的是这一份）；
        // 验价响应把它挂在酒店节点上。解析不出则回落查价时点的那份，两者皆无即空——不猜（R-5.4）
        DidaHotel confirmedHotel = data.firstHotel();
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.getCheckIn(), confirmedHotel == null ? null : confirmedHotel.getCancellationPolicyList());
        if (cancelPolicy.isEmpty()) {
            cancelPolicy = productKeyDeriver.convertCancelPolicy(
                    request.getCheckIn(), searched.getRatePlanCancellationPolicyList());
            log.info("道旅验价：验价响应退改不可解析，回落查价条款,sHotelId={},ratePlanId={},回落后条数={}",
                    request.getSHotelId(), confirmed.getRatePlanId(), cancelPolicy.size());
        }
        List<PriceInfo> priceInfos = buildPriceInfos(confirmed.getPriceList());
        log.info("道旅验价：通过并签发句柄,sHotelId={},ratePlanId={},salePrice={}分,offerId={},退改条数={},每日价条数={}",
                request.getSHotelId(), confirmed.getRatePlanId(), salePriceCents, offerId,
                cancelPolicy.size(), priceInfos.size());
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.DIDA.getCode()))
                .salePrice(salePriceCents)
                .subPrice(salePriceCents)
                .currencyType(currency)
                .cancelPolicy(cancelPolicy)
                .priceInfos(priceInfos)
                .build();
    }

    private DidaPriceConfirmRequest buildConfirmRequest(CheckPriceReq request, DidaHotel hotel, DidaRatePlan plan) {
        DidaPriceConfirmRequest confirmRequest = new DidaPriceConfirmRequest();
        confirmRequest.setHeader(header());
        confirmRequest.setHotelId(hotel.getHotelId());
        confirmRequest.setRatePlanId(plan.getRatePlanId());
        confirmRequest.setCheckInDate(request.getCheckIn());
        confirmRequest.setCheckOutDate(request.getCheckOut());
        confirmRequest.setNationality(properties.getNationality());
        confirmRequest.setNumOfRooms(roomsOf(request));
        confirmRequest.setCurrency(properties.getCurrency());
        // 下单前那一档必须 PreBook=true——ReferenceNo 只在这时下发
        confirmRequest.setPreBook(true);
        confirmRequest.setIsNeedOnRequest(false);
        // 有就原样回传、没有就不传（官方注 11：禁止解码或改写；本账号实测不下发该字段）
        confirmRequest.setMetadata(StringUtils.trimToNull(plan.getMetadata()));
        confirmRequest.setOccupancyDetails(occupancyDetails(request));
        return confirmRequest;
    }

    private List<DidaPriceConfirmRequest.OccupancyDetail> occupancyDetails(CheckPriceReq request) {
        int rooms = roomsOf(request);
        int adults = request.getAdultCount() == null ? 1 : request.getAdultCount();
        int children = request.getChildNum() == null ? 0 : request.getChildNum();
        List<Integer> ages = request.getChildAges() == null ? List.of() : request.getChildAges();
        List<DidaPriceConfirmRequest.OccupancyDetail> details = new ArrayList<>(rooms);
        for (int roomNum = 1; roomNum <= rooms; roomNum++) {
            DidaPriceConfirmRequest.OccupancyDetail detail = new DidaPriceConfirmRequest.OccupancyDetail();
            detail.setRoomNum(roomNum);
            detail.setAdultCount(adults);
            detail.setChildCount(children);
            detail.setChildAgeDetails(children > 0 ? ages : null);
            details.add(detail);
        }
        return details;
    }

    /** 发一次 pricesearch；调用失败或响应体缺失返回 null（网络类失败由调用方落 INDETERMINATE） */
    private DidaPriceSearchResponse search(Long hotelId, String checkIn, String checkOut, Integer roomNum,
                                           Integer adults, Integer children, List<Integer> childAges,
                                           CallPurpose purpose) {
        DidaPriceSearchRequest searchRequest = new DidaPriceSearchRequest();
        searchRequest.setHeader(header());
        searchRequest.setHotelIdList(Collections.singletonList(hotelId));
        searchRequest.setCheckInDate(checkIn);
        searchRequest.setCheckOutDate(checkOut);
        searchRequest.setCurrency(properties.getCurrency());
        searchRequest.setNationality(properties.getNationality());
        DidaPriceSearchRequest.IsRealTime realTime = new DidaPriceSearchRequest.IsRealTime();
        // 取最全的那一档：2026-09-08 实测单店实时 ≥ 单店非实时（8 家里 7 家逐条相同，
        // 1 家实时多 3 条）。它与逐店查询是一对——多店下开实时反而丢货，见请求模型的注释
        realTime.setValue(true);
        realTime.setRoomCount(roomNum == null || roomNum <= 0 ? 1 : roomNum);
        searchRequest.setIsRealTime(realTime);
        DidaPriceSearchRequest.RealTimeOccupancy occupancy = new DidaPriceSearchRequest.RealTimeOccupancy();
        occupancy.setAdultCount(adults == null || adults <= 0 ? 1 : adults);
        occupancy.setChildCount(children == null ? 0 : children);
        occupancy.setChildAgeDetails(children != null && children > 0 ? childAges : null);
        searchRequest.setRealTimeOccupancy(occupancy);
        searchRequest.setIsNeedOnRequest(false);

        ResponseResult<DidaPriceSearchResponse> result =
                new PriceSearchAccess(properties).access(searchRequest, purpose);
        return result == null ? null : result.getData();
    }

    private DidaRequestHeader header() {
        return new DidaRequestHeader(properties.getClientId(), properties.getLicenseKey());
    }

    /** 逐晚价条数必须等于住期天数，缺一天就无法按住期报价 */
    private static boolean hasFullDayPrices(DidaRatePlan plan, int nights) {
        List<DidaPriceItem> prices = plan.getPriceList();
        if (CollectionUtils.isEmpty(prices) || nights <= 0 || prices.size() != nights) {
            return false;
        }
        return prices.stream().allMatch(p -> p.getPrice() != null && p.getPrice().signum() > 0);
    }

    /** 住期总价（分）。优先取供应商的 TotalPrice，缺失时按逐晚价合计 */
    private static Integer totalCentsOf(DidaRatePlan plan) {
        BigDecimal total = plan.getTotalPrice();
        if (total == null && CollectionUtils.isNotEmpty(plan.getPriceList())) {
            total = plan.getPriceList().stream()
                    .map(DidaPriceItem::getPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return total == null ? null : Money.toCents(total);
    }

    private static List<PriceInfo> buildPriceInfos(List<DidaPriceItem> prices) {
        if (CollectionUtils.isEmpty(prices)) {
            return List.of();
        }
        return prices.stream()
                .map(p -> PriceInfo.builder()
                        .date(stayDate(p.getStayDate()))
                        .price(p.getPrice() == null ? 0 : Money.toCents(p.getPrice()))
                        .build())
                .collect(Collectors.toList());
    }

    /** {@code 2026-09-29 00:00:00} → {@code 2026-09-29}；形状不符时原样透出 */
    private static String stayDate(String raw) {
        if (raw == null) {
            return null;
        }
        int space = raw.indexOf(' ');
        return space > 0 ? raw.substring(0, space) : raw;
    }

    private static String roomTypeIdOf(DidaRatePlan plan) {
        return plan.getRoomTypeId() == null ? null : String.valueOf(plan.getRoomTypeId());
    }

    /** 房型名优先中文（RoomName_CN），缺失回落英文 */
    private static String roomNameOf(DidaRatePlan plan) {
        return StringUtils.defaultIfBlank(plan.getRoomNameCn(), plan.getRoomName());
    }

    private static Long hotelIdOf(String sHotelId) {
        try {
            return Long.valueOf(StringUtils.trimToEmpty(sHotelId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int roomsOf(CheckPriceReq request) {
        return request.getRoomNum() == null || request.getRoomNum() <= 0 ? 1 : request.getRoomNum();
    }

    private static int nightsOf(String checkIn, String checkOut) {
        try {
            return (int) ChronoUnit.DAYS.between(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String childAgesCsv(List<Integer> ages) {
        return CollectionUtils.isEmpty(ages) ? ""
                : ages.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String terminalMessage(CheckPriceOutcome outcome) {
        return switch (outcome) {
            case SOLD_OUT -> "该产品已售罄";
            case RATE_DEAD -> "请重新查价后再选择";
            default -> "未能确认该产品是否可订";
        };
    }

    private static CheckPriceRespDTO outcome(CheckPriceOutcome outcome, String message) {
        return CheckPriceRespDTO.builder().outcome(outcome).message(message).build();
    }
}
