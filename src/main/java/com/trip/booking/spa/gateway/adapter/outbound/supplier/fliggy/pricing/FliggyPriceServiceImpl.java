package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
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
            Meal meal = productKeyDeriver.convertMeal(rate.get("meals"));
            List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(rate.get("cancel_policy"));
            String roomId = text(rate, "room_id");
            String roomName = text(rate, "room_name");
            // 身份与成分一次算出（R-2.8）；rate_key 是易腐报价码，与 productKey 永不同字段
            ProductIdentity identity = productKeyDeriver.deriveIdentity(sHotelId, roomId, meal,
                    cancelPolicy, occupancy);
            Integer exclusive = intOrNull(totalRate, "exclusive");
            products.add(ProductRespDTO.builder()
                    .hotelId(sHotelId)
                    .productId(rateKey)
                    .productKey(identity.productKey())
                    .identity(identity)
                    .supplierId(SupplierSourceEnum.FLIGGY.getCode())
                    .room(Room.builder().roomId(roomId).roomName(roomName).build())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build())
                    .currencyType(text(totalRate, "currency"))
                    .totalPrice(inclusive)
                    .roomTotalPrice(exclusive == null ? inclusive : exclusive)
                    .meal(meal)
                    .cancelPolicy(cancelPolicy)
                    .maxOccupancy(request.getAdultNum())
                    .build());
        }
        log.info("飞猪查价：转换完成,hotelId={},checkIn={},报价总数={},出报={},跳过_缺票据={},跳过_缺总价={}",
                sHotelId, request.getCheckIn(), rates.size(), products.size(), skippedNoRateKey, skippedNoPrice);
        countDropped(DropReason.NO_SESSION_CREDENTIALS, skippedNoRateKey);
        countDropped(DropReason.NO_DAY_PRICE, skippedNoPrice);
        return products;
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
                .cancelPolicy(productKeyDeriver.convertCancelPolicy(fresh.get("cancel_policy")))
                .message("验价通过")
                .build();
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
        // 文档标 String 类型、值为分：canConvertToInt 覆盖数字与数字串两种形态
        try {
            return Integer.parseInt(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
