package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

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
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.checkprice.client.ValidateAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.client.AriAvailabilityAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyValidateResponse;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.util.DateUtil;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞猪查价与验价的协议本体（契约见 docs/fliggy/distribution-api.md）。
 * 验价=现取现验：重新查价取<b>同一响应</b>里的新 rate_key 与 request_trace_id
 * （票据配对，跨响应混用即错），validate 换 create_key，双钥与验价总价/币种进 OfferStore。
 * 平台层凭据病走 AUTH_CONFIG；业务层码义未核实一律不确定，绝不判无房。
 */
@Slf4j
@Service
public class FliggyPriceServiceImpl {

    private static final String METHOD_ARI = "taobao.xhotel.distribution.ari.availability";
    private static final String METHOD_VALIDATE = "taobao.xhotel.order.international.distribution.validate";

    @Resource
    private FliggyProperties properties;
    @Resource
    private FliggyProductKeyDeriver productKeyDeriver;
    @Resource
    private OfferStore offerStore;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService priceCacheService;

    /**
     * 刷价入口（口径同艺龙 {@code queryPricesCache}）：没问出结果返回 null 不动缓存
     * （F-5.1，一次网络抖动不许清在售价）；明确无货（含下架）返回空列表并照走
     * {@code productToCache}——空列表打无货标记，僵尸价随之清掉（B7）。
     */
    public List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier) {
        PricingResult result = queryPrices(request, supplier, CallPurpose.REFRESH);
        if (result.outcome() == com.trip.booking.spa.gateway.domain.booking.PricingOutcome.INDETERMINATE) {
            return null;
        }
        List<ProductRespDTO> products = result.products();
        priceCacheService.productToCache(products, request, supplier);
        return products;
    }

    // ---------- 查价 ----------

    public PricingResult queryPrices(PriceReq request, Supplier supplier, CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("飞猪查价：凭证未配置（FLIGGY_APP_KEY/FLIGGY_SECRET/FLIGGY_SESSION），无法调用,sHotelId={}",
                    supplier.getSHotelId());
            return PricingResult.indeterminate();
        }
        request.setOccupancies(Occupancy.perRoom(request.getRoomNum(), request.getAdultNum(),
                request.getChildNum(), request.getChildAges()));

        ResponseResult<FliggyAriResponse> result = new AriAvailabilityAccess(properties)
                .access(ariCall(supplier.getSHotelId(), request.getCheckIn(), request.getCheckout(),
                        request.getAdultNum(), request.getChildNum(), request.getChildAges()), purpose);
        FliggyAriResponse resp = result == null ? null : result.getData();
        if (resp == null) {
            log.warn("飞猪查价：调用未取得结果,sHotelId={},checkIn={}", supplier.getSHotelId(), request.getCheckIn());
            return PricingResult.indeterminate();
        }
        if (resp.isHotelDelisted()) {
            // 资源已下架=明确无货(cursor 生产实证语义),折进不确定会无限重试下架店
            log.info("飞猪查价：酒店已下架,sHotelId={}", supplier.getSHotelId());
            return PricingResult.noInventory();
        }
        if (resp.isPlatformError()) {
            reportPlatformError("查价", resp, supplier.getSHotelId());
            return PricingResult.indeterminate();
        }
        if (resp.isEmptyResult()) {
            // 答了但没有：飞猪明确该住期无可售（与「没问出结果」分开，B7）
            return PricingResult.noInventory();
        }
        return PricingResult.of(convertRates(resp.rates(), request, supplier.getSHotelId()));
    }

    List<ProductRespDTO> convertRates(List<JsonNode> rates, PriceReq request, String sHotelId) {
        List<ProductRespDTO> products = new ArrayList<>();
        String occupancy = request.getOccupancies().get(0);
        int skippedNoRateKey = 0;
        int skippedNoPrice = 0;
        int skippedNoDayPrice = 0;
        for (JsonNode rate : rates) {
            String rateKey = text(rate, "rate_key");
            if (StringUtils.isBlank(rateKey)) {
                // 有价无票据不可成交（同艺龙缺马甲的语义）——丢弃必须可数（O-4.5）
                skippedNoRateKey++;
                continue;
            }
            JsonNode totalRate = rate.get("total_rate");
            Integer inclusive = intOrNull(totalRate, "inclusive");
            if (inclusive == null) {
                skippedNoPrice++;
                continue;
            }
            List<PriceInfo> priceInfos = convertPriceInfos(rate, request.getCheckIn(), request.getCheckout());
            if (priceInfos == null) {
                skippedNoDayPrice++;
                continue;
            }
            Meal meal = productKeyDeriver.convertMeal(rate.get("meals"));
            List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), rate.get("cancel_policy"));
            String roomId = text(rate, "room_id");
            String roomName = text(rate, "room_name");
            // 产品名=卖法名（口径同艺龙 RatePlanName 回落房型名）：一个房型多个卖法，
            // 全填房型名的话档案里同房型的行无从分辨
            String planName = StringUtils.defaultIfBlank(text(rate, "rate_plan_name"), roomName);
            // 身份与成分一次算出（R-2.8）；rate_key 是易腐报价码，与 productKey 永不同字段
            ProductIdentity identity = productKeyDeriver.deriveIdentity(sHotelId, roomId, meal,
                    cancelPolicy, occupancy, inclusive);
            Integer exclusive = intOrNull(totalRate, "exclusive");
            products.add(ProductRespDTO.builder()
                    .hotelId(sHotelId)
                    .productId(rateKey)
                    .productKey(identity.productKey())
                    .identity(identity)
                    .supplierId(SupplierSourceEnum.FLIGGY.getCode())
                    .room(Room.builder().roomId(roomId).roomName(roomName).build())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(planName).build())
                    .currencyType(text(totalRate, "currency"))
                    .totalPrice(inclusive)
                    .roomTotalPrice(exclusive == null ? inclusive : exclusive)
                    .priceInfos(priceInfos)
                    .meal(meal)
                    .cancelPolicy(cancelPolicy)
                    .maxOccupancy(request.getAdultNum())
                    .build());
        }
        log.info("飞猪查价：转换完成,hotelId={},checkIn={},报价总数={},出报={},跳过_缺票据={},跳过_缺总价={},跳过_缺逐日价={}",
                sHotelId, request.getCheckIn(), rates.size(), products.size(), skippedNoRateKey, skippedNoPrice,
                skippedNoDayPrice);
        countDropped(DropReason.NO_SESSION_CREDENTIALS, skippedNoRateKey);
        countDropped(DropReason.NO_DAY_PRICE, skippedNoPrice + skippedNoDayPrice);
        return products;
    }

    /**
     * 逐日价——写缓存的唯一载体：{@code productToCache} 只认 {@code priceInfos}，
     * 总价字段进不了价格 Hash。口径同艺龙 {@code buildPriceInfos}：price=含税、
     * roomPrice=税前（缺则回落含税）、taxes=差额。单晚缺 daily_rates 可用
     * total_rate 精确回落；多晚缺任何一天即整条不报——均摊会造出假的日期价。
     */
    private List<PriceInfo> convertPriceInfos(JsonNode rate, String checkIn, String checkOut) {
        List<String> dates = DateUtil.getDatesBetween(checkIn, checkOut);
        if (dates.isEmpty()) {
            return null;
        }
        Map<String, JsonNode> dailyByDate = new HashMap<>();
        JsonNode dailyRates = rate.get("daily_rates");
        if (dailyRates != null && dailyRates.isArray()) {
            for (JsonNode day : dailyRates) {
                String date = text(day, "date");
                if (StringUtils.isNotBlank(date)) {
                    dailyByDate.put(date, day);
                }
            }
        }
        List<PriceInfo> priceInfos = new ArrayList<>();
        for (String date : dates) {
            JsonNode day = dailyByDate.get(date);
            if (day == null && dates.size() == 1) {
                day = rate.get("total_rate");
            }
            Integer inclusive = intOrNull(day, "inclusive");
            if (inclusive == null) {
                return null;
            }
            Integer exclusive = intOrNull(day, "exclusive");
            int roomPrice = exclusive == null ? inclusive : exclusive;
            priceInfos.add(PriceInfo.builder().date(date).price(inclusive)
                    .roomPrice(roomPrice).taxes(inclusive - roomPrice).build());
        }
        return priceInfos;
    }

    // ---------- 验价（现取现验） ----------

    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        if (!properties.isConfigured()) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "飞猪凭证未配置，未能确认该产品是否可订");
        }
        ResponseResult<FliggyAriResponse> ariResult = new AriAvailabilityAccess(properties)
                .access(ariCall(request.getSHotelId(), request.getCheckIn(), request.getCheckOut(),
                        request.getAdultCount(), request.getChildNum(), request.getChildAges()),
                        CallPurpose.CHECK_PRICE);
        FliggyAriResponse ari = ariResult == null ? null : ariResult.getData();
        if (ari == null) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "查价未取得结果，请稍后重试");
        }
        // 验价即刷(F-6 即时半边):现取的这份全店现货验完即弃等于白白留着缓存陈价对外报,
        // 异步回写、不占验价预算(艺龙同名机制的实证:河内 Daewoo 陈价每次点击 RATE_DEAD)
        freshPricesToCacheAsync(request, ari);
        if (ari.isHotelDelisted()) {
            return outcome(CheckPriceOutcome.SOLD_OUT, "该酒店已被供应商下架");
        }
        if (ari.isPlatformError()) {
            reportPlatformError("验价·现取", ari, request.getSHotelId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商平台拒绝了请求，未能确认");
        }
        if (ari.isEmptyResult()) {
            return outcome(CheckPriceOutcome.SOLD_OUT, "该住期已无任何可售报价");
        }
        JsonNode fresh = findByRateKey(ari.rates(), request.getSProductId());
        if (fresh == null) {
            // 所点报价不在当前响应：重新查价往往同房型仍有房——RATE_DEAD 不可折叠进不确定
            return outcome(CheckPriceOutcome.RATE_DEAD, "所选报价已不在当前在售列表，请重新查价");
        }
        if (request.getVerifyLevel() == VerifyLevel.AVAILABILITY) {
            // 渠道曝光档：到此为止，不打 validate（艺龙同款，见
            // ElongPriceServiceImpl#availabilityOnlyResp）。飞猪 validate 实测 1,833ms
            // （2026-09-02 生产），加上现取这趟塞不进渠道 1.5s 的核价预算——超时被兜底成
            // 「不可预订」，报价就在列表页被抹掉（高德实测 SUPPLIER_BUDGET_TIMEOUT，
            // RT 2312ms）。只回"有货"、不签句柄：rate_key/create_key 到真下单必已过期
            return availabilityOnlyResp(request, fresh);
        }
        String freshRateKey = text(fresh, "rate_key");
        String traceId = ari.requestTraceId();

        ResponseResult<FliggyValidateResponse> validateResult = new ValidateAccess(properties)
                .access(validateCall(freshRateKey, traceId, request), CallPurpose.CHECK_PRICE);
        FliggyValidateResponse validate = validateResult == null ? null : validateResult.getData();
        if (validate == null) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价未取得结果，请稍后重试");
        }
        if (validate.isPlatformError()) {
            reportPlatformError("验价", validate, request.getSHotelId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商平台拒绝了请求，未能确认");
        }
        if (!validate.isSucc()) {
            // 业务层失败：官方码表空白，码义未核实一律不确定，绝不判无房
            log.warn("飞猪验价：业务层未通过,sHotelId={},rateKey={},bizErrorCode={}",
                    request.getSHotelId(), freshRateKey, validate.bizErrorCode());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未确认该报价可订，请稍后重试");
        }
        String createKey = validate.createKey();
        Integer totalCents = validate.totalRoomPriceCents();
        String currency = validate.currencyCode();
        if (StringUtils.isBlank(createKey) || totalCents == null || StringUtils.isBlank(currency)) {
            // 价格与钥匙不许猜：少任何一样都无法安全下单
            log.error("飞猪验价：响应缺关键字段,createKey空={},total空={},currency空={}",
                    StringUtils.isBlank(createKey), totalCents == null, StringUtils.isBlank(currency));
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价响应不完整，未能确认该产品是否可订");
        }

        Map<String, String> credentials = new HashMap<>();
        credentials.put(FliggyOfferCredentials.RATE_KEY, freshRateKey);
        credentials.put(FliggyOfferCredentials.CREATE_KEY, createKey);
        credentials.put(FliggyOfferCredentials.TOTAL_ROOM_PRICE_CENTS, String.valueOf(totalCents));
        credentials.put(FliggyOfferCredentials.CURRENCY_CODE, currency);
        if (StringUtils.isNotBlank(traceId)) {
            credentials.put(FliggyOfferCredentials.REQUEST_TRACE_ID, traceId);
        }
        String offerId = offerStore.issue(SupplierSourceEnum.FLIGGY.getCode(), credentials);
        if (offerId == null) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.FLIGGY.getCode()))
                .salePrice(totalCents)
                .totalPriceAfter(totalCents)
                .currencyType(currency)
                // 退改以验价时点的同一报价为准（现取现验，与查价同一响应）
                .cancelPolicy(productKeyDeriver.convertCancelPolicy(request.getCheckIn(), fresh.get("cancel_policy")))
                .message("验价通过")
                .build();
    }

    /**
     * 曝光档验价：只答"这条报价还在售"，不答"能不能立刻下单"。
     *
     * <p>证据是<b>现取的这份 ARI</b>（与 BOOKABLE 档同一份响应），不是缓存——所以它是
     * 真实的在售判定，只是省掉 validate 那一趟。价格与退改都是 ARI 口径，用于展示；
     * 真正对账的数在下单前那一档由 validate 给出。
     *
     * <p>不签句柄是硬约束（模板 {@code AbstractCheckPriceSyncSupportService} 只对 BOOKABLE
     * 要求句柄）：飞猪的 create_key 由 validate 签发，此档根本没调它。
     */
    CheckPriceRespDTO availabilityOnlyResp(CheckPriceReq request, JsonNode fresh) {
        JsonNode totalRate = fresh.get("total_rate");
        Integer inclusive = intOrNull(totalRate, "inclusive");
        if (inclusive == null) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出总价，未能确认该产品");
        }
        String currency = text(totalRate, "currency");
        if (StringUtils.isBlank(currency)) {
            // 币种不许缺也不许猜：上游把美元数字当人民币用即 7 倍资损（SpaCurrencyConverter 同纪律）
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出币种，未能确认该产品");
        }
        List<PriceInfo> priceInfos = convertPriceInfos(fresh, request.getCheckIn(), request.getCheckOut());
        if (priceInfos == null) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出每日价，未能确认该产品");
        }
        // salePrice 整单口径：ARI 的 total_rate 是单间价，多间须乘间数（与 BOOKABLE 档一致，
        // 那档的 validate 总价本就按 number_of_rooms 算）
        int rooms = request.getRoomNum() == null ? 1 : request.getRoomNum();
        int totalCents = inclusive * rooms;
        List<CancelPolicy> cancelPolicy =
                productKeyDeriver.convertCancelPolicy(request.getCheckIn(), fresh.get("cancel_policy"));
        log.info("飞猪验价(仅现货)：有货但未验证可订性,sHotelId={},rateKey={},价格={}分{},退改条数={}",
                request.getSHotelId(), text(fresh, "rate_key"), totalCents, currency, cancelPolicy.size());
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.AVAILABLE)
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(currency)
                .cancelPolicy(cancelPolicy)
                .priceInfos(priceInfos)
                .message("有货，未验证可订性（曝光档）")
                .build();
    }

    // ---------- 验价即刷回写 ----------

    /** 回写线程:单线程+有界队列+满则弃——宁可丢一次回写(下轮刷价会补),不许积压拖验价 */
    private static final java.util.concurrent.ExecutorService FRESH_PRICES_POOL =
            com.trip.booking.spa.platform.concurrent.ThreadPools.serialBounded("fliggy-fresh-prices", 64, true);

    void freshPricesToCacheAsync(CheckPriceReq request, FliggyAriResponse ari) {
        try {
            FRESH_PRICES_POOL.execute(() -> freshPricesToCache(request, ari));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("验价即刷：回写队列满，本次丢弃(下轮刷价会补) sHotelId={}", request.getSHotelId());
        }
    }

    /**
     * 回写本体（同步，供测试直接驱动）。口径与查价同源：下架/明确无货回写空列表
     * （打无货标记清僵尸价 B7）；平台/业务错误不动缓存（F-5.1）;占用键随验价走。
     */
    void freshPricesToCache(CheckPriceReq request, FliggyAriResponse ari) {
        try {
            PriceReq priceReq = PriceReq.builder()
                    .checkIn(request.getCheckIn())
                    .checkout(request.getCheckOut())
                    .roomNum(request.getRoomNum() == null ? 1 : request.getRoomNum())
                    .adultNum(request.getAdultCount())
                    .childNum(request.getChildNum() == null ? 0 : request.getChildNum())
                    .childAges(request.getChildAges() == null ? new ArrayList<>() : request.getChildAges())
                    .build();
            priceReq.setOccupancies(Occupancy.perRoom(priceReq.getRoomNum(), priceReq.getAdultNum(),
                    priceReq.getChildNum(), priceReq.getChildAges()));
            List<ProductRespDTO> products;
            if (ari.isHotelDelisted() || ari.isEmptyResult()) {
                products = List.of();
            } else if (!ari.isSucc()) {
                return;
            } else {
                products = convertRates(ari.rates(), priceReq, request.getSHotelId());
            }
            priceCacheService.productToCache(products, priceReq, Supplier.builder()
                    .supplierId(SupplierSourceEnum.FLIGGY.getCode())
                    .sHotelId(request.getSHotelId()).build());
        } catch (Exception e) {
            log.warn("验价即刷：回写失败,不影响验价 sHotelId={}", request.getSHotelId(), e);
        }
    }

    // ---------- 装配与工具 ----------

    private FliggyTopCall ariCall(String sHotelId, String checkIn, String checkOut,
                                  Integer adults, Integer childNum, List<Integer> childAges) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("check_in", checkIn);
        query.put("check_out", checkOut);
        query.put("adults", adults);
        query.put("children", childNum == null ? 0 : childNum);
        if (childNum != null && childNum > 0 && childAges != null) {
            query.put("children_ages", childAges.stream().map(String::valueOf).toList());
        }
        query.put("hotel_id", sHotelId);
        query.put("language", "zh_CN");
        query.put("distributor", properties.getDistributor());
        return new FliggyTopCall(METHOD_ARI, Map.of("availability_query", JsonUtils.writeObject2Json(query)));
    }

    private FliggyTopCall validateCall(String rateKey, String traceId, CheckPriceReq request) {
        int rooms = request.getRoomNum() == null ? 1 : request.getRoomNum();
        List<Map<String, Object>> occupancies = new ArrayList<>();
        for (int i = 0; i < rooms; i++) {
            Map<String, Object> room = new LinkedHashMap<>();
            room.put("room_no", i + 1);
            room.put("adult_num", request.getAdultCount());
            room.put("children_num", request.getChildNum() == null ? 0 : request.getChildNum());
            if (request.getChildNum() != null && request.getChildNum() > 0 && request.getChildAges() != null) {
                room.put("children_ages", request.getChildAges().stream().map(String::valueOf).toList());
            }
            occupancies.add(room);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("rate_key", rateKey);
        req.put("number_of_rooms", rooms);
        req.put("check_in", request.getCheckIn());
        req.put("check_out", request.getCheckOut());
        req.put("occupancies", occupancies);
        req.put("distributor", properties.getDistributor());
        if (StringUtils.isNotBlank(traceId)) {
            req.put("request_trace_id", traceId);
        }
        // intention 三值语义官方未说明（快照 §9 必测清单第 5 项），确认前不传
        return new FliggyTopCall(METHOD_VALIDATE, Map.of("validate_req", JsonUtils.writeObject2Json(req)));
    }

    static JsonNode findByRateKey(List<JsonNode> rates, String rateKey) {
        if (StringUtils.isBlank(rateKey)) {
            return null;
        }
        for (JsonNode rate : rates) {
            if (rateKey.equals(text(rate, "rate_key"))) {
                return rate;
            }
        }
        return null;
    }

    /** 平台层错误上报：凭据病走 AUTH_CONFIG（指标+[auth-config] 日志锚），其余原样落日志 */
    private void reportPlatformError(String phase, FliggyTopResponse resp, String sHotelId) {
        if (resp.isCredentialFailure()) {
            Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.FLIGGY));
            log.error("[auth-config] 飞猪{}：我方凭据/配置病（session 过期或签名错），供应商无辜、"
                    + "重试无效、需人工处理。platformError={},sHotelId={}", phase, resp.platformError(), sHotelId);
            return;
        }
        log.warn("飞猪{}：平台层拒绝,platformError={},sHotelId={}", phase, resp.platformError(), sHotelId);
    }

    private static void countDropped(DropReason reason, int count) {
        if (count > 0) {
            Monitor.recordMany(MetricNames.QUOTE_DROPPED,
                    MetricTags.dropped(SupplierSourceEnum.FLIGGY, FunnelStage.CONVERT, reason), count);
        }
    }

    private static CheckPriceRespDTO outcome(CheckPriceOutcome outcome, String message) {
        return CheckPriceRespDTO.builder().outcome(outcome).message(message).build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        // 文档标 String 类型、值为分：parseInt(asText) 覆盖数字与数字串两种形态
        //（canConvertToInt 对文本节点恒 false，不能用——deriver 曾因它丢光退改规则）
        try {
            return Integer.parseInt(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
