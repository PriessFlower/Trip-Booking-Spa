package com.trip.booking.spa.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.bff.client.RapidGateway;
import com.trip.booking.spa.bff.client.RapidReply;
import com.trip.booking.spa.bff.config.BffProperties;
import com.trip.booking.spa.bff.offer.OfferCache;
import com.trip.booking.spa.bff.store.OrderStore;
import com.trip.booking.spa.bff.store.PropertyContentRepo;
import com.trip.booking.spa.bff.web.BffException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 交易链路：下单 → 查单 → 取消。
 *
 * <p>TR1：affiliate_reference_id 在验价成功时预绑定于 bookToken，重复提交复用同一 ID；
 * 下单超时/空响应按「不确定」处理并立即用同一参考 ID 反查确证，绝不当作失败重下。
 *
 * <p>§3.4 过渡方案：发给 Expedia 的旅客与账单信息为固定联系人（待与 Expedia 商定），
 * 旅客真实姓名只存本地 bff_order。
 */
@Slf4j
@Service
public class BffBookingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RapidGateway gateway;
    private final OfferCache offerCache;
    private final OrderStore orderStore;
    private final PropertyContentRepo contentRepo;
    private final BffProperties props;
    private final ExpediaRapidProperties rapidProperties;

    public BffBookingService(RapidGateway gateway, OfferCache offerCache, OrderStore orderStore,
                                PropertyContentRepo contentRepo,
                                BffProperties props, ExpediaRapidProperties rapidProperties) {
        this.gateway = gateway;
        this.offerCache = offerCache;
        this.orderStore = orderStore;
        this.contentRepo = contentRepo;
        this.props = props;
        this.rapidProperties = rapidProperties;
    }

    // ---------- 下单 ----------

    public JsonNode book(String bookToken, String travelerGivenName, String travelerFamilyName,
                         String travelerEmail, String travelerPhone, String propertyName) {
        return book(bookToken, travelerGivenName, travelerFamilyName, travelerEmail, travelerPhone,
                propertyName, null);
    }

    public JsonNode book(String bookToken, String travelerGivenName, String travelerFamilyName,
                         String travelerEmail, String travelerPhone, String propertyName,
                         String testScenario) {
        if (!rapidProperties.isBookingEnabled()) {
            throw new BffException(503, "下单未启用（expedia.booking-enabled=false）；"
                    + "沙箱验收需以 --expedia.booking-enabled=true 启动");
        }
        OfferCache.BookOffer offer = offerCache.getBook(bookToken);
        if (offer == null) {
            throw new BffException(410, "下单令牌已过期，请重新验价");
        }
        String orderId = offer.orderId;
        String travelerName = safeName(travelerGivenName) + " " + safeName(travelerFamilyName);

        // 幂等：同一 bookToken 重复提交，若订单已成功直接回放结果（TR1 / TR7 重复提交保护）
        Optional<OrderStore.OrderRow> existing = orderStore.find(orderId);
        if (existing.isPresent() && "booked".equals(existing.get().status)) {
            return buildBookResult(existing.get());
        }

        ObjectNode request = buildBookRequest(orderId, offer);
        if (existing.isEmpty()) {
            OrderStore.OrderRow row = new OrderStore.OrderRow();
            row.orderId = orderId;
            row.propertyId = offer.rate.propertyId;
            row.propertyName = propertyName;
            row.checkin = offer.rate.checkin;
            row.checkout = offer.rate.checkout;
            row.occupancy = String.join("|", offer.rate.occupancies);
            row.bedDescription = offer.rate.bedDescription;
            row.travelerName = travelerName;
            row.travelerEmail = travelerEmail;
            row.travelerPhone = travelerPhone;
            row.status = "booking";
            row.requestJson = request.toString();
            row.pricingJson = offer.pricingJson;
            row.policyJson = buildPolicySnapshot(offer.rate, offer.paymentOptionsJson);
            orderStore.insert(row);
        }

        RapidReply reply = gateway.post("booking", offer.bookHref, request.toString(), testScenario);
        return classifyBookingReply(orderId, reply, testScenario);
    }

    /**
     * 下单结果三态分类（对齐 core ExpediaBookingClassifier 的纪律）：
     * itinerary_id → 成功；重复参考 ID → 反查确证；网络异常/超时/5xx → 反查确证；
     * 其余 4xx → 确定性失败。
     */
    private JsonNode classifyBookingReply(String orderId, RapidReply reply, String testScenario) {
        JsonNode body = reply.getBody();
        String itineraryId = body == null ? null : body.path("itinerary_id").asText(null);

        if (itineraryId != null && !itineraryId.isEmpty()) {
            orderStore.update(orderId, itineraryId, "booked", reply.getRaw());
            // 下单响应可能不含确认号，回查一次补全每间房的 confirmation_id（TR6）
            retrieveAndSync(orderId, testScenario);
            return buildBookResult(orderStore.find(orderId).orElseThrow());
        }

        String raw = reply.getRaw() == null ? "" : reply.getRaw();
        boolean duplicate = raw.contains("duplicate_itinerary");

        if (duplicate || reply.isIndeterminate()) {
            // TR1/TR7：超时、空响应或重复参考 ID —— 用同一参考 ID 查单确证，绝不盲目重下
            log.warn("下单结果不确定 orderId={} status={} duplicate={}，反查确证",
                    orderId, reply.getStatus(), duplicate);
            JsonNode retrieved = retrieveAndSync(orderId, testScenario);
            Optional<OrderStore.OrderRow> row = orderStore.find(orderId);
            if (retrieved != null && row.isPresent() && row.get().itineraryId != null) {
                return buildBookResult(row.get());
            }
            orderStore.update(orderId, null, "unknown", raw.isEmpty() ? null : raw);
            throw new BffException(502, "下单结果不确定，且反查未找到订单；请勿重复支付，稍后在“我的行程”确认");
        }

        // 确定性失败
        String message = body == null ? raw : body.path("message").asText(raw);
        orderStore.update(orderId, null, "failed", raw.isEmpty() ? null : raw);
        throw new BffException(422, "下单失败: " + message);
    }

    private ObjectNode buildBookRequest(String orderId, OfferCache.BookOffer offer) {
        BffProperties.Contact contact = props.getContact();
        ObjectNode request = MAPPER.createObjectNode();
        request.put("affiliate_reference_id", orderId);
        request.put("hold", false);
        request.put("email", contact.getEmail());
        ObjectNode phone = request.putObject("phone");
        phone.put("country_code", contact.getPhoneCountryCode());
        phone.put("number", contact.getPhoneNumber());
        ArrayNode roomsNode = request.putArray("rooms");
        for (int i = 0; i < offer.rate.occupancies.size(); i++) {
            ObjectNode room = roomsNode.addObject();
            room.put("given_name", contact.getGivenName());
            room.put("family_name", contact.getFamilyName());
        }
        ArrayNode payments = request.putArray("payments");
        ObjectNode payment = payments.addObject();
        payment.put("type", "affiliate_collect");
        ObjectNode billing = payment.putObject("billing_contact");
        billing.put("given_name", contact.getGivenName());
        billing.put("family_name", contact.getFamilyName());
        ObjectNode address = billing.putObject("address");
        address.put("line_1", contact.getAddressLine1());
        address.put("city", contact.getCity());
        address.put("state_province_code", contact.getStateProvinceCode());
        address.put("postal_code", contact.getPostalCode());
        address.put("country_code", contact.getAddressCountryCode());
        return request;
    }

    // ---------- 查单 ----------

    public JsonNode getOrder(String orderId) {
        OrderStore.OrderRow row = orderStore.find(orderId)
                .orElseThrow(() -> new BffException(404, "订单不存在: " + orderId));
        if (!"failed".equals(row.status)) {
            retrieveAndSync(orderId);
            row = orderStore.find(orderId).orElseThrow();
        }
        return buildBookResult(row);
    }

    public JsonNode listOrders() {
        ArrayNode orders = MAPPER.createArrayNode();
        for (OrderStore.OrderRow row : orderStore.listRecent(20)) {
            orders.add(buildBookResult(row));
        }
        return orders;
    }

    /** 以 affiliate_reference_id + 下单邮箱反查（TR1 的确证通道），并同步本地状态 */
    private JsonNode retrieveAndSync(String orderId) {
        return retrieveAndSync(orderId, null);
    }

    /** Test 场景的 mock 订单在真实查单中不存在，反查须携带同一 Test 头 */
    private JsonNode retrieveAndSync(String orderId, String testScenario) {
        String path = "/v3/itineraries?affiliate_reference_id=" + encode(orderId)
                + "&email=" + encode(props.getContact().getEmail());
        RapidReply reply = gateway.get("retrieve", path, testScenario);
        if (!reply.is2xx() || reply.getBody() == null || !reply.getBody().isArray()
                || reply.getBody().isEmpty()) {
            return null;
        }
        JsonNode itinerary = reply.getBody().path(0);
        String itineraryId = itinerary.path("itinerary_id").asText(null);
        String status = mapRoomsStatus(itinerary);
        orderStore.update(orderId, itineraryId, status, itinerary.toString());
        return itinerary;
    }

    private String mapRoomsStatus(JsonNode itinerary) {
        boolean anyBooked = false;
        boolean allCanceled = true;
        for (JsonNode room : itinerary.path("rooms")) {
            String status = room.path("status").asText("");
            if ("booked".equals(status) || "pending".equals(status)) {
                anyBooked = true;
            }
            if (!"canceled".equals(status)) {
                allCanceled = false;
            }
        }
        if (allCanceled && itinerary.path("rooms").size() > 0) {
            return "canceled";
        }
        return anyBooked ? "booked" : "unknown";
    }

    // ---------- 取消 ----------

    /** 逐间取消：多房间订单必须逐个取消每个 Confirmation ID（TR6） */
    public JsonNode cancelOrder(String orderId) {
        return cancelOrder(orderId, null);
    }

    public JsonNode cancelOrder(String orderId, String testScenario) {
        OrderStore.OrderRow row = orderStore.find(orderId)
                .orElseThrow(() -> new BffException(404, "订单不存在: " + orderId));
        JsonNode itinerary = retrieveAndSync(orderId, testScenario);
        if (itinerary == null) {
            throw new BffException(502, "查单未能确证订单状态，暂不能取消，请稍后重试");
        }
        int canceled = 0;
        int failed = 0;
        for (JsonNode room : itinerary.path("rooms")) {
            if ("canceled".equals(room.path("status").asText())) {
                continue;
            }
            String cancelHref = room.path("links").path("cancel").path("href").asText(null);
            if (cancelHref == null) {
                failed++;
                continue;
            }
            RapidReply reply = gateway.delete("cancel", cancelHref, testScenario);
            if (reply.is2xx()) {
                canceled++;
            } else {
                failed++;
                log.warn("房间取消失败 orderId={} room={} status={}", orderId,
                        room.path("id").asText(), reply.getStatus());
            }
        }
        retrieveAndSync(orderId, testScenario);
        OrderStore.OrderRow updated = orderStore.find(orderId).orElseThrow();
        ObjectNode result = (ObjectNode) buildBookResult(updated);
        result.put("canceledRooms", canceled);
        result.put("failedRooms", failed);
        if (failed > 0) {
            result.put("cancelWarning", "部分房间取消失败，请重试或联系客服");
        }
        return result;
    }

    // ---------- 输出 ----------

    /** 订单视图：本地记录 + 供应商响应中的标识符（CP1/ER1：itinerary_id、每间房 confirmation_id） */
    private JsonNode buildBookResult(OrderStore.OrderRow row) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("orderId", row.orderId);
        result.put("itineraryId", row.itineraryId);
        result.put("propertyId", row.propertyId);
        result.put("propertyName", row.propertyName);
        result.put("checkin", row.checkin);
        result.put("checkout", row.checkout);
        result.put("occupancy", row.occupancy);
        result.put("bedDescription", row.bedDescription);
        result.put("travelerName", row.travelerName);
        result.put("travelerEmail", row.travelerEmail);
        result.put("travelerPhone", row.travelerPhone);
        result.put("status", row.status);
        result.put("createdAt", row.createdAt);
        JsonNode pricing = parseQuietly(row.pricingJson);
        if (pricing != null) {
            result.set("pricing", pricing);
            if (pricing.isObject() && pricing.fields().hasNext()) {
                JsonNode taxesAndFees = PricingMath.taxesAndFees(pricing.fields().next().getValue());
                if (taxesAndFees != null) {
                    result.set("taxesAndFees", taxesAndFees);
                }
            }
        }
        JsonNode policy = parseQuietly(row.policyJson);
        if (policy != null) {
            result.set("policy", policy);
        }
        contentRepo.findById(row.propertyId, props.getLanguage()).ifPresent(property -> {
            if (property.raw == null) {
                return;
            }
            ObjectNode content = result.putObject("propertyContent");
            content.put("address", property.raw.path("address").path("line_1").asText(null));
            if (property.starRating != null) {
                content.put("starRating", property.starRating);
            }
            content.set("checkinPolicy", property.raw.path("checkin"));
            content.set("checkoutPolicy", property.raw.path("checkout"));
            content.set("fees", property.raw.path("fees"));
            content.set("policies", property.raw.path("policies"));
        });
        ArrayNode roomsOut = result.putArray("rooms");
        JsonNode response = parseQuietly(row.responseJson);
        if (response != null) {
            for (JsonNode room : response.path("rooms")) {
                ObjectNode roomOut = roomsOut.addObject();
                roomOut.put("status", room.path("status").asText(null));
                JsonNode confirmation = room.path("confirmation_id");
                if (!confirmation.isMissingNode()) {
                    roomOut.put("confirmationIdExpedia", confirmation.path("expedia").asText(null));
                    roomOut.put("confirmationIdProperty", confirmation.path("property").asText(null));
                }
            }
        }
        return result;
    }

    /** 政策快照：确认页/凭证展示所需的 refundable、取消政策与付款信息（BP3/BP8/BP10/ER） */
    private String buildPolicySnapshot(OfferCache.RateOffer rate, String paymentOptionsJson) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("refundable", rate.refundable);
        snapshot.put("merchantOfRecord", rate.merchantOfRecord);
        JsonNode cancelPenalties = parseQuietly(rate.cancelPenaltiesJson);
        if (cancelPenalties != null) {
            snapshot.set("cancelPenalties", cancelPenalties);
        }
        JsonNode nonrefundable = parseQuietly(rate.nonrefundableDateRangesJson);
        if (nonrefundable != null) {
            snapshot.set("nonrefundableDateRanges", nonrefundable);
        }
        JsonNode paymentOptions = parseQuietly(paymentOptionsJson);
        if (paymentOptions != null) {
            snapshot.set("paymentOptions", paymentOptions);
        }
        return snapshot.toString();
    }

    private JsonNode parseQuietly(String json) {
        try {
            return json == null ? null : MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "-" : name.trim();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
