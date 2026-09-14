package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing.client.PriceAccess;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyTokenProvider;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyDayPrice;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRoomType;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.Meal;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.ProductInfo;
import com.trip.booking.spa.gateway.domain.product.Room;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.StayDates;

/**
 * 差旅无忧查价：供应商响应 → 可售产品。验价那几个钩子在 {@code ClwyCheckPriceServiceImpl}，
 * 供应商侧的读法集中在本类。
 *
 * <p><b>价格口径（B4）</b>：官方 05-product-price 的 {@code price} 是「每天每间价格」。
 * 本仓对外的 {@code totalPrice} 按 B4 要求统一为「<b>全部间数 × 全部夜</b> × 含税 × 单一币种」，
 * 故 = Σ逐日价 × 间数。这一条不是可选的写法偏好——docs/gateway-boundary.md B4 点名
 * 「clwy 单间/全间数致成本落账错」，就是这里搞混的后果。
 *
 * <p>{@code FeeList} 是<b>到店付</b>的费或税，不含在报价内，故不参与总价、也不报进 totalTaxes。
 */
@Slf4j
@Service
public class ClwyPriceServiceImpl implements ClwyPriceService {

    @Resource
    private ClwyProperties properties;

    @Resource
    private ClwyTokenProvider tokenProvider;

    @Resource
    private ClwyProductKeyDeriver productKeyDeriver;

    @Resource
    private OfferStore offerStore;

    @Override
    public PricingResult queryPrices(PriceQuery request, CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("clwy 查价：凭证未配置,sHotelId={}", request.supplierHotelId());
            return PricingResult.indeterminate();
        }
        if (StringUtils.isBlank(request.supplierHotelId())) {
            log.error("clwy 查价：酒店 id 为空，无法调用");
            return PricingResult.indeterminate();
        }
        PriceQuery withOccupancy = request.occupancies() == null || request.occupancies().isEmpty()
                ? request.toBuilder().occupancies(Occupancy.perRoom(request.roomNum(), request.adultNum(),
                        request.childNum(), request.childAges())).build()
                : request;
        ClwyPriceResponse data = fetch(withOccupancy, null, purpose);
        if (data == null) {
            log.warn("clwy 查价：调用未取得结果,sHotelId={},checkIn={}",
                    withOccupancy.supplierHotelId(), withOccupancy.checkIn());
            return PricingResult.indeterminate();
        }
        return toPricingResult(data, withOccupancy);
    }

    /**
     * 响应 → 三态。本家只有两个码（{@link com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyCodes}），
     * 失败原因只在 message 里，故判据只能立在文案上，且只立<b>有生产实证</b>的那一条。
     */
    PricingResult toPricingResult(ClwyPriceResponse data, PriceQuery request) {
        if (!data.isSucc()) {
            if (data.isNoAvailability()) {
                // cursor 生产一周 427/427 的唯一失败文案：这家这住期没货
                log.info("clwy 查价：无库存,sHotelId={},checkIn={}", request.supplierHotelId(), request.checkIn());
                return PricingResult.noInventory();
            }
            // 表外文案：没有任何实证说明它意味着什么，一律不确定——绝不当成无货去清缓存
            log.warn("clwy 查价：业务失败且文案未登记，按不确定处理,sHotelId={},code={},message={}",
                    request.supplierHotelId(), data.getCode(), data.getMessage());
            return PricingResult.indeterminate();
        }
        if (data.isEmptyResult()) {
            // 报价档表达"没货"的常规形态：code=200、hotelList 为 null、无 message
            // （2026-09-14 生产实测 T+200/T+330 即如此）。故记 info 不记 warn
            log.info("clwy 查价：无库存(200 空列表),sHotelId={},checkIn={}",
                    request.supplierHotelId(), request.checkIn());
            return PricingResult.noInventory();
        }
        List<Product> products = convert(data.firstHotel(), request);
        return products.isEmpty() ? PricingResult.noInventory() : PricingResult.available(products);
    }

    /** 现货 → 产品列表。每个丢弃分支都要计 {@code quote_dropped}（architecture.md §5 第二步） */
    List<Product> convert(ClwyHotel hotel, PriceQuery request) {
        List<Product> products = new ArrayList<>();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getRoomTypeList())) {
            return products;
        }
        int nights = StayDates.nights(request.checkIn(), request.checkOut());
        int rooms = Math.max(1, request.roomNum());
        String occupancy = request.occupancies() == null || request.occupancies().isEmpty()
                ? null : request.occupancies().get(0);
        int hourRoom = 0;
        int notOnSale = 0;
        int noDayPrice = 0;
        int mixedCurrency = 0;
        int zeroPrice = 0;

        for (ClwyRoomType roomType : hotel.getRoomTypeList()) {
            if (roomType == null || CollectionUtils.isEmpty(roomType.getRatePlanList())
                    || StringUtils.isBlank(roomType.getRoomTypeId())) {
                continue;
            }
            for (ClwyRatePlan plan : roomType.getRatePlanList()) {
                if (plan == null || StringUtils.isBlank(plan.getRatePlanId())) {
                    continue;
                }
                if (plan.hourRoom()) {
                    // 钟点房的住期语义与整夜房不同，按整夜口径卖出去就是卖错
                    hourRoom++;
                    continue;
                }
                List<ClwyDayPrice> days = plan.getRatePlanPriceList();
                if (CollectionUtils.isEmpty(days) || days.size() != nights) {
                    // 逐晚价不齐即无法给出住期总价（也无法做逐晚明细），不猜缺的那晚
                    noDayPrice++;
                    continue;
                }
                if (days.stream().anyMatch(d -> d == null || !d.onSale())) {
                    // 任一晚 flag=0（下架），这段住期就不可售
                    notOnSale++;
                    continue;
                }
                String currency = singleCurrency(days);
                if (currency == null) {
                    // 逐晚币种不一致：总价无法在单一币种下相加（B4），不猜汇率
                    mixedCurrency++;
                    continue;
                }
                Integer totalCents = totalCents(days, rooms, currency);
                if (totalCents == null || totalCents <= 0) {
                    zeroPrice++;
                    continue;
                }
                products.add(convertPlan(hotel.getHotelId(), roomType, plan, days,
                        totalCents, currency, occupancy, request));
            }
        }
        countDropped(DropReason.HOUR_ROOM, hourRoom);
        countDropped(DropReason.NOT_ON_SALE, notOnSale);
        countDropped(DropReason.NO_DAY_PRICE, noDayPrice);
        countDropped(DropReason.MIXED_CURRENCY, mixedCurrency);
        countDropped(DropReason.ZERO_TOTAL_PRICE, zeroPrice);
        log.info("clwy 查价：转换完成,hotelId={},checkIn={},出报={},跳过_钟点房={},跳过_下架={},"
                        + "跳过_逐晚价不齐={},跳过_币种不一={},跳过_零价={}",
                hotel.getHotelId(), request.checkIn(), products.size(),
                hourRoom, notOnSale, noDayPrice, mixedCurrency, zeroPrice);
        return products;
    }

    private Product convertPlan(String hotelId, ClwyRoomType roomType, ClwyRatePlan plan,
                                List<ClwyDayPrice> days, int totalCents, String currency,
                                String occupancy, PriceQuery request) {
        Meal meal = productKeyDeriver.convertMeal(plan);
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), plan.getCancellationPenalties(), plan.getCityTimeZone());
        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
        ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelId, roomType.getRoomTypeId(),
                meal, cancelPolicy, occupancy, totalCents);
        Product product = Product.builder()
                .hotelId(hotelId)
                // 报价标识=ratePlanId（分代轮换的易腐码）；身份=productKey，二者永不同字段（R-2.3）
                .productId(plan.getRatePlanId())
                .productKey(identity.productKey())
                .identity(identity)
                .supplierId(SupplierSourceEnum.CLWY.getCode())
                .room(Room.builder().roomId(roomType.getRoomTypeId()).roomName(roomName(roomType)).build())
                .productInfo(ProductInfo.builder()
                        // 可售间数取逐晚最小值——住期内任一晚只剩 N 间，整段就只能卖 N 间
                        .inventory(minRoomCount(days))
                        .productStatus(1)
                        .productName(StringUtils.isNotBlank(plan.getRatePlanNameCn())
                                ? plan.getRatePlanNameCn() : roomName(roomType))
                        .build())
                .currencyType(currency)
                // B4：全部间数 × 全部夜。逐晚明细仍按单间给（明细的自然口径），故两者不等价
                .totalPrice(totalCents)
                .priceInfos(buildPriceInfos(days))
                .meal(meal)
                .cancelPolicy(cancelPolicy)
                .build();
        // FeeList 是到店付，不在报价内，故此处报 0——不把到店付的税费算进我们卖的这个价
        product.setTotalTaxes(0);
        return product;
    }

    /** 逐晚币种必须一致；不一致或缺失返回 null（调用方据此丢弃并计数） */
    static String singleCurrency(List<ClwyDayPrice> days) {
        String currency = null;
        for (ClwyDayPrice day : days) {
            String c = StringUtils.trimToNull(day.getCurrency());
            if (c == null) {
                return null;
            }
            if (currency == null) {
                currency = c;
            } else if (!currency.equalsIgnoreCase(c)) {
                return null;
            }
        }
        return currency == null ? null : currency.toUpperCase(java.util.Locale.ROOT);
    }

    /** Σ逐日价 × 间数（B4）。任一晚价格缺失返回 null */
    static Integer totalCents(List<ClwyDayPrice> days, int rooms, String currency) {
        BigDecimal perRoom = BigDecimal.ZERO;
        for (ClwyDayPrice day : days) {
            if (day.getPrice() == null) {
                return null;
            }
            perRoom = perRoom.add(day.getPrice());
        }
        try {
            return (int) Money.fromYuan(perRoom.multiply(BigDecimal.valueOf(rooms)), currency).amountCents();
        } catch (Exception e) {
            return null;
        }
    }

    /** 住期内可售间数取逐晚最小值；任一晚缺失按未知（null）——不拿其余晚的数去填 */
    static Integer minRoomCount(List<ClwyDayPrice> days) {
        Integer min = null;
        for (ClwyDayPrice day : days) {
            if (day.getRoomCount() == null) {
                return null;
            }
            min = min == null ? day.getRoomCount() : Math.min(min, day.getRoomCount());
        }
        return min;
    }

    /** 逐晚明细按<b>单间</b>给：这是明细的自然口径，与总价的全间数口径刻意不同，见类注释 B4 段 */
    static List<PriceInfo> buildPriceInfos(List<ClwyDayPrice> days) {
        List<PriceInfo> infos = new ArrayList<>(days.size());
        for (ClwyDayPrice day : days) {
            infos.add(PriceInfo.builder()
                    .date(day.getDate())
                    .price(Money.toCents(day.getPrice()))
                    .build());
        }
        return infos;
    }

    /** 发一次 GetPrice；{@code ratePlanId} 非空即试单档（回 RateKey）。调用失败返回 null */
    ClwyPriceResponse fetch(PriceQuery request, String ratePlanId, CallPurpose purpose) {
        ClwyPriceRequest body = new ClwyPriceRequest();
        body.setHotelId(request.supplierHotelId());
        body.setRatePlanId(StringUtils.trimToNull(ratePlanId));
        body.setCheckInDate(request.checkIn());
        body.setCheckOutDate(request.checkOut());
        body.setRoomCount(Math.max(1, request.roomNum()));
        // 官方参数表：这三项是「每间房」的人数，不是总数
        body.setAdultCount(Math.max(1, request.adultNum()));
        body.setChildCount(request.childNum());
        body.setChildAges(CollectionUtils.isEmpty(request.childAges()) ? null : request.childAges());
        // 报价/验价/下单必须一致（官方 CountryCode 字段说明）
        body.setCountryCode(properties.getCountryCode());
        PriceAccess access = ratePlanId == null
                ? PriceAccess.forQuote(properties, tokenProvider)
                : PriceAccess.forTrial(properties, tokenProvider);
        ResponseResult<ClwyPriceResponse> result = access.access(body, purpose);
        return result == null ? null : result.getData();
    }

    private static String roomName(ClwyRoomType roomType) {
        return StringUtils.isNotBlank(roomType.getRoomTypeNameCn())
                ? roomType.getRoomTypeNameCn() : roomType.getRoomTypeNameEn();
    }

    /**
     * 本类的丢弃计数：只绑定"是哪一家、丢在哪一层"，怎么记在 {@link Monitor#recordDropped}。
     * 这个切分就是 §4.1.3 的判据——换一家供应商要改的只有这两个常量。
     */
    private static void countDropped(DropReason reason, int count) {
        Monitor.recordDropped(SupplierSourceEnum.CLWY, FunnelStage.CONVERT, reason, count);
    }

    // ── 验价链路的钩子（编排在 AbstractCheckPriceFlow，供应商侧的读法在这里）─────────────

    /** 前置校验：凭证与酒店 id。二者都属"供应商侧什么都没发生"，故是确定态而非不确定 */
    public CheckPriceResult precondition(CheckPriceCommand request) {
        if (!properties.isConfigured()) {
            log.error("clwy 验价：凭证未配置,sHotelId={}", request.supplierHotelId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "供应商凭证未配置，未能确认该产品是否可订");
        }
        if (StringUtils.isBlank(request.supplierHotelId())) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "酒店标识为空，未能确认该产品是否可订");
        }
        return null;
    }

    /**
     * 现取整店现货。<b>本家的 {@code No Availability} 在这一步才分得清是哪一种</b>：
     * 它既可能是「所点报价码已换代」也可能是「整店真没货」，而这一步问的是整店——
     * 整店都回无货，那就是真没货（SOLD_OUT）；整店有货只是那张票不见了，才是票死（RATE_DEAD），
     * 由后续 resolve 去换等价票。这正是把判据建在流程上而不是猜文案的原因。
     */
    public LiveStock<ClwyHotel> fetchLiveStock(CheckPriceCommand request) {
        PriceQuery asQuery = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode())
                .supplierHotelId(request.supplierHotelId())
                .checkIn(request.checkIn()).checkOut(request.checkOut())
                .roomNum(request.roomNum())
                .adultNum(request.adultCount() == null ? 1 : request.adultCount())
                .childNum(request.childNum())
                .childAges(request.childAges() == null ? List.of() : request.childAges())
                .build();
        PriceQuery withOccupancy = asQuery.toBuilder().occupancies(Occupancy.perRoom(
                asQuery.roomNum(), asQuery.adultNum(), asQuery.childNum(), asQuery.childAges())).build();
        ClwyPriceResponse data = fetch(withOccupancy, null, CallPurpose.CHECK_PRICE);
        if (data == null) {
            log.warn("clwy 验价：现货查询未取得结果,sHotelId={},sProductId={}",
                    request.supplierHotelId(), request.supplierProductId());
            return LiveStock.terminal(CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE,
                    "现货查询未取得结果，未能确认该产品是否可订，请稍后重试"));
        }
        // 验价即刷：闭包捕获这份现货，终态分支也带着它返回——整店无售正是要落无货标记的时候
        java.util.function.Function<PriceQuery, List<Product>> fresh =
                priceReq -> convert(data.firstHotel(), priceReq);
        if (!data.isSucc()) {
            CheckPriceOutcome terminal = data.isNoAvailability()
                    ? CheckPriceOutcome.SOLD_OUT : CheckPriceOutcome.INDETERMINATE;
            log.info("clwy 验价：现货查询业务失败,sHotelId={},code={},message={},判定={}",
                    request.supplierHotelId(), data.getCode(), data.getMessage(), terminal);
            return LiveStock.<ClwyHotel>terminal(CheckPriceResult.of(terminal, terminal == CheckPriceOutcome.SOLD_OUT
                    ? "该酒店该住期已售罄" : "现货查询失败，未能确认该产品是否可订")).freshConvertedBy(fresh);
        }
        ClwyHotel hotel = data.firstHotel();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getRoomTypeList())) {
            log.info("clwy 验价：该店该住期无在售产品,sHotelId={}", request.supplierHotelId());
            return LiveStock.<ClwyHotel>terminal(CheckPriceResult.of(CheckPriceOutcome.SOLD_OUT,
                    "该酒店该住期已无在售产品")).freshConvertedBy(fresh);
        }
        return LiveStock.of(hotel).freshConvertedBy(fresh);
    }

    /** 按报价码在现货里找那张票。找不到即该票已换代，由模板转入 resolve */
    public ClwyPlan findPlan(ClwyHotel hotel, String ratePlanId) {
        if (hotel == null || StringUtils.isBlank(ratePlanId) || CollectionUtils.isEmpty(hotel.getRoomTypeList())) {
            return null;
        }
        for (ClwyRoomType roomType : hotel.getRoomTypeList()) {
            if (roomType == null || CollectionUtils.isEmpty(roomType.getRatePlanList())) {
                continue;
            }
            for (ClwyRatePlan plan : roomType.getRatePlanList()) {
                if (plan != null && ratePlanId.equals(plan.getRatePlanId())) {
                    return new ClwyPlan(roomType, plan);
                }
            }
        }
        return null;
    }

    /**
     * resolve 候选：现货里与请求 productKey 等价的票，带各自价格供容差门比对。
     * 钟点房与逐晚价不齐的票不进候选——它们本来就不该被卖出去，换票换到它们等于绕过转换期的过滤。
     */
    public List<ResolveCandidate<ClwyPlan>> resolveCandidates(ClwyHotel hotel, CheckPriceCommand request) {
        List<ResolveCandidate<ClwyPlan>> candidates = new ArrayList<>();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getRoomTypeList())) {
            return candidates;
        }
        int nights = StayDates.nights(request.checkIn(), request.checkOut());
        int rooms = Math.max(1, request.roomNum());
        String occupancy = Occupancy.perRoom(rooms, request.adultCount() == null ? 1 : request.adultCount(),
                request.childNum(), request.childAges() == null ? List.of() : request.childAges()).get(0);
        for (ClwyRoomType roomType : hotel.getRoomTypeList()) {
            if (roomType == null || CollectionUtils.isEmpty(roomType.getRatePlanList())
                    || StringUtils.isBlank(roomType.getRoomTypeId())) {
                continue;
            }
            for (ClwyRatePlan plan : roomType.getRatePlanList()) {
                if (plan == null || plan.hourRoom()) {
                    continue;
                }
                List<ClwyDayPrice> days = plan.getRatePlanPriceList();
                if (CollectionUtils.isEmpty(days) || days.size() != nights
                        || days.stream().anyMatch(d -> d == null || !d.onSale())) {
                    continue;
                }
                String currency = singleCurrency(days);
                if (currency == null) {
                    continue;
                }
                Integer totalCents = totalCents(days, rooms, currency);
                if (totalCents == null || totalCents <= 0) {
                    continue;
                }
                Meal meal = productKeyDeriver.convertMeal(plan);
                List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                        request.checkIn(), plan.getCancellationPenalties(), plan.getCityTimeZone());
                String key = productKeyDeriver.deriveProductKey(hotel.getHotelId(), roomType.getRoomTypeId(),
                        meal, cancelPolicy, occupancy, totalCents);
                if (key.equals(request.productKey())) {
                    candidates.add(new ResolveCandidate<>(new ClwyPlan(roomType, plan), totalCents));
                }
            }
        }
        return candidates;
    }

    /** 曝光档：有货即回，不签句柄（不打试单、不占 RateKey 的 10 分钟） */
    public CheckPriceResult availabilityOnlyResp(CheckPriceCommand request, ClwyPlan candidate) {
        List<ClwyDayPrice> days = candidate.plan().getRatePlanPriceList();
        String currency = singleCurrency(days);
        Integer totalCents = currency == null ? null : totalCents(days, Math.max(1, request.roomNum()), currency);
        if (totalCents == null || totalCents <= 0) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "供应商未给出可用价格，未能确认该产品");
        }
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), candidate.plan().getCancellationPenalties(), candidate.plan().getCityTimeZone());
        log.info("clwy 验价(仅现货)：有货但未试单,sHotelId={},ratePlanId={},价格={}分,退改条数={}",
                request.supplierHotelId(), candidate.ratePlanId(), totalCents, cancelPolicy.size());
        return CheckPriceResult.builder()
                .outcome(CheckPriceOutcome.AVAILABLE)
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(currency)
                .cancelPolicy(cancelPolicy)
                .priceInfos(buildPriceInfos(days))
                .build();
    }


    /**
     * 试单：带 {@code RatePlanId} 再打一次 GetPrice，拿 {@code RateKey} 并签句柄。
     *
     * <p><b>价格以试单这一次回的为准</b>：官方把带 RatePlanId 的调用称作「预定页试单」，它返回的
     * 是该报价此刻的真实价与条款；查价那次只是列表价。两者不一致时用后者，等于拿列表价去下单。
     *
     * <p><b>没拿到 RateKey 就不许说可订</b>：RateKey 是下单唯一入口，缺了它这个"可订"结论
     * 在下一步必然兑现不了——把不确定说成确定，正是 B6 点名的那类错。
     */
    public CheckPriceResult validate(CheckPriceCommand request, ClwyHotel hotel, ClwyPlan candidate) {
        PriceQuery asQuery = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode())
                .supplierHotelId(request.supplierHotelId())
                .checkIn(request.checkIn()).checkOut(request.checkOut())
                .roomNum(request.roomNum())
                .adultNum(request.adultCount() == null ? 1 : request.adultCount())
                .childNum(request.childNum())
                .childAges(request.childAges() == null ? List.of() : request.childAges())
                .build();
        ClwyPriceResponse data = fetch(asQuery, candidate.ratePlanId(), CallPurpose.CHECK_PRICE);
        if (data == null) {
            log.warn("clwy 试单：未取得结果,sHotelId={},ratePlanId={}",
                    request.supplierHotelId(), candidate.ratePlanId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "试单未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        if (!data.isSucc()) {
            // 试单阶段的 No Availability：整店现货刚刚还在（能走到这一步就说明找到了票），
            // 这里再回无货，说明是这张票没了——重新查价即可拿到换代后的报价
            CheckPriceOutcome terminal = data.isNoAvailability()
                    ? CheckPriceOutcome.RATE_DEAD : CheckPriceOutcome.INDETERMINATE;
            log.info("clwy 试单：业务失败,sHotelId={},ratePlanId={},code={},message={},判定={}",
                    request.supplierHotelId(), candidate.ratePlanId(), data.getCode(), data.getMessage(), terminal);
            return CheckPriceResult.of(terminal, terminal == CheckPriceOutcome.RATE_DEAD
                    ? "该产品已不可订，请重新查价后再选择" : "试单未取得确定结果，未能确认该产品是否可订");
        }
        ClwyPlan confirmed = findPlan(data.firstHotel(), candidate.ratePlanId());
        if (confirmed == null) {
            log.info("clwy 试单：响应未回传所点报价，判为死票,sHotelId={},ratePlanId={}",
                    request.supplierHotelId(), candidate.ratePlanId());
            return CheckPriceResult.of(CheckPriceOutcome.RATE_DEAD, "该产品已不可订，请重新查价后再选择");
        }
        String rateKey = StringUtils.trimToNull(data.getRateKey());
        if (rateKey == null) {
            log.error("clwy 试单：未回 RateKey，无法下单,sHotelId={},ratePlanId={}",
                    request.supplierHotelId(), candidate.ratePlanId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "试单未取得下单校验码，未能确认该产品是否可订");
        }
        return buildBookable(request, confirmed, rateKey);
    }

    private CheckPriceResult buildBookable(CheckPriceCommand request, ClwyPlan confirmed, String rateKey) {
        int rooms = Math.max(1, request.roomNum());
        List<ClwyDayPrice> days = confirmed.plan().getRatePlanPriceList();
        String currency = days == null ? null : singleCurrency(days);
        Integer totalCents = currency == null ? null : totalCents(days, rooms, currency);
        if (totalCents == null || totalCents <= 0) {
            log.error("clwy 试单：验后价缺失或币种不一,sHotelId={},ratePlanId={}",
                    request.supplierHotelId(), confirmed.ratePlanId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "试单未回可用价格，未能确认该产品是否可订");
        }
        java.util.Map<String, String> credentials = new java.util.HashMap<>();
        credentials.put(ClwyOfferCredentials.RATE_KEY, rateKey);
        credentials.put(ClwyOfferCredentials.HOTEL_ID, request.supplierHotelId());
        credentials.put(ClwyOfferCredentials.ROOM_TYPE_ID, confirmed.roomTypeId());
        credentials.put(ClwyOfferCredentials.RATE_PLAN_ID, confirmed.ratePlanId());
        credentials.put(ClwyOfferCredentials.CHECK_IN, request.checkIn());
        credentials.put(ClwyOfferCredentials.CHECK_OUT, request.checkOut());
        credentials.put(ClwyOfferCredentials.ROOM_NUM, String.valueOf(rooms));
        credentials.put(ClwyOfferCredentials.ADULT_COUNT,
                String.valueOf(request.adultCount() == null ? 1 : request.adultCount()));
        credentials.put(ClwyOfferCredentials.CHILD_AGES, Occupancy.childAgesCsv(request.childAges()));
        credentials.put(ClwyOfferCredentials.DECLARED_TOTAL,
                BigDecimal.valueOf(totalCents).movePointLeft(2).toPlainString());
        credentials.put(ClwyOfferCredentials.CURRENCY, currency);
        credentials.put(ClwyOfferCredentials.COUNTRY_CODE, properties.getCountryCode());
        // 官方：验价回了非 null 非 0 的 Tag 就必须回传。0 与 null 同义（普通渠道），一律存空串
        Integer tag = confirmed.plan().getTag();
        credentials.put(ClwyOfferCredentials.TAG,
                tag == null || tag == 0 ? "" : String.valueOf(tag));
        String offerId = offerStore.issue(SupplierSourceEnum.CLWY.getCode(), credentials);
        if (StringUtils.isBlank(offerId)) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), confirmed.plan().getCancellationPenalties(), confirmed.plan().getCityTimeZone());
        log.info("clwy 试单：通过并签发句柄,sHotelId={},ratePlanId={},salePrice={}分{},offerId={},退改条数={}",
                request.supplierHotelId(), confirmed.ratePlanId(), totalCents, currency, offerId, cancelPolicy.size());
        return CheckPriceResult.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.CLWY.getCode()))
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(currency)
                .cancelPolicy(cancelPolicy)
                .priceInfos(buildPriceInfos(days))
                .build();
    }

    /** 仅供测试构造场景使用 */
    public void setProperties(ClwyProperties properties) {
        this.properties = properties;
    }

    /** 仅供测试构造场景使用 */
    public void setProductKeyDeriver(ClwyProductKeyDeriver productKeyDeriver) {
        this.productKeyDeriver = productKeyDeriver;
    }
}
