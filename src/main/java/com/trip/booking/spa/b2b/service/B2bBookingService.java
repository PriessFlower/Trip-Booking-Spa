package com.trip.booking.spa.b2b.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.b2b.config.B2bProperties;
import com.trip.booking.spa.b2b.store.AgreementStore;
import com.trip.booking.spa.b2b.store.OrderOwnerStore;
import com.trip.booking.spa.b2b.web.B2bException;
import com.trip.booking.spa.bff.service.BffBookingService;
import com.trip.booking.spa.bff.store.OrderStore;
import com.trip.booking.spa.bff.store.PropertyContentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * B2B 侧的下单与订单查询：在 bff 的下单链路外面加两道判定——协议已签、订单归属本人。
 *
 * <p>下单本体不重写。bff 的 {@link BffBookingService} 已承担下单三态、重复参考 ID 的
 * 反查确证与幂等回放（这些是 Expedia 技术要求里最难的部分），复制一遍只会多一处会漂的判定。
 */
@Slf4j
@Service
public class B2bBookingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 订单列表页取多少单 */
    private static final int LIST_LIMIT = 20;

    /** 入住单要给境外酒店前台看，故酒店联系信息与房型名一律取英文行 */
    private static final String VOUCHER_LANGUAGE = "en-US";

    /** 确认单给代理商看，取与站内展示一致的中文行 */
    private static final String DISPLAY_LANGUAGE = "zh-CN";

    private final BffBookingService bookingService;
    private final OrderStore orderStore;
    private final OrderOwnerStore ownerStore;
    private final AgreementStore agreementStore;
    private final PropertyContentRepo contentRepo;
    private final B2bProperties props;

    public B2bBookingService(BffBookingService bookingService, OrderStore orderStore,
                             OrderOwnerStore ownerStore, AgreementStore agreementStore,
                             PropertyContentRepo contentRepo, B2bProperties props) {
        this.bookingService = bookingService;
        this.orderStore = orderStore;
        this.ownerStore = ownerStore;
        this.agreementStore = agreementStore;
        this.contentRepo = contentRepo;
        this.props = props;
    }

    /**
     * 下单。先卡协议：未接受当前版本的下游代理协议一律拒绝，这是验收要求
     * 「下游代理在预订前阅读并接受」的后半段，只在前端拦不算数。
     */
    public JsonNode book(String agentId, String bookToken, String givenName, String familyName,
                         String email, String phone, String propertyName, String test,
                         JsonNode rateAmenities) {
        requireAgreement(agentId);
        JsonNode result = bookingService.book(bookToken, givenName, familyName,
                email, phone, propertyName, test);
        String orderId = result == null ? null : result.path("orderId").asText(null);
        if (orderId == null || orderId.isBlank()) {
            // 下单已经发生过了，只是回执里没有单号；不能当作失败重下
            log.error("下单回执缺少 orderId，无法记归属 agentId={}", agentId);
            return result;
        }
        ownerStore.claim(orderId, agentId, amenitiesJson(rateAmenities));
        return result;
    }

    /**
     * 房价包含项由前端从房价查询结果原样回传：该字段只在查价响应里有，
     * 验价与下单响应都不带，不在此刻留档，事后无从得知这单含不含早。
     *
     * <p>只接受字符串数组，其余形状一律丢弃并记日志——宁可缺这一项，
     * 也不要往库里写一段来路不明的 JSON。
     */
    private String amenitiesJson(JsonNode rateAmenities) {
        if (rateAmenities == null || !rateAmenities.isArray() || rateAmenities.isEmpty()) {
            return null;
        }
        for (JsonNode item : rateAmenities) {
            if (!item.isTextual()) {
                log.warn("房价包含项不是字符串数组，已丢弃: {}", rateAmenities);
                return null;
            }
        }
        return rateAmenities.toString();
    }

    /**
     * 订单列表。只出本地已知字段，不逐单反查供应商——列表页发 20 次反查会把
     * 供应商配额和页面加载一起拖死；状态以详情页为准。
     */
    public JsonNode listOrders(String agentId) {
        ArrayNode orders = MAPPER.createArrayNode();
        for (OrderOwnerStore.OwnerRow owner : ownerStore.listRecent(agentId, LIST_LIMIT)) {
            orderStore.find(owner.orderId).ifPresent(row -> {
                ObjectNode node = brief(row);
                setAmenities(node, owner.rateAmenities);
                orders.add(node);
            });
        }
        return orders;
    }

    /** 把落库的包含项原文挂回订单视图；解析不了就当没有，不让一行坏数据毁掉整个列表 */
    private void setAmenities(ObjectNode node, String rateAmenities) {
        if (rateAmenities == null || rateAmenities.isBlank()) {
            return;
        }
        try {
            node.set("rateAmenities", MAPPER.readTree(rateAmenities));
        } catch (Exception e) {
            log.warn("房价包含项不可解析 orderId={}", node.path("orderId").asText(), e);
        }
    }

    /**
     * 订单详情。走 bff 的查单（含向 Expedia 反查同步状态），前置归属校验，
     * 再补一个 {@code hotelContact} 节点供入住单使用。
     */
    public JsonNode getOrder(String agentId, String orderId) {
        requireOwnership(agentId, orderId);
        JsonNode order = bookingService.getOrder(orderId);
        if (order instanceof ObjectNode node) {
            String propertyId = node.path("propertyId").asText(null);
            ObjectNode contact = hotelContact(propertyId);
            if (contact != null) {
                node.set("hotelContact", contact);
            }
            ownerStore.find(orderId).ifPresent(owner -> setAmenities(node, owner.rateAmenities));
            String[] ids = bookedRoomIds(orderId);
            if (ids != null) {
                ObjectNode roomEn = roomContent(propertyId, ids[0], ids[1], VOUCHER_LANGUAGE);
                if (roomEn != null) {
                    node.set("roomEn", roomEn);
                }
                ObjectNode roomZh = roomContent(propertyId, ids[0], ids[1], DISPLAY_LANGUAGE);
                if (roomZh != null) {
                    node.set("roomZh", roomZh);
                }
            }
        }
        return order;
    }

    /**
     * 订单实际预订的房型号与床型组号，取自供应商下单响应的 {@code rooms[0]}。
     *
     * <p>订单表只落了中文床型、没落房型，也没落这两个标识符；但它们在响应原文里，
     * 用它们就能按需反查任意语言的房型名，比新增落库字段稳妥——存量订单也能补上。
     *
     * @return {roomId, bedGroupId}；取不到返回 null
     */
    private String[] bookedRoomIds(String orderId) {
        Optional<OrderStore.OrderRow> row = orderStore.find(orderId);
        if (row.isEmpty() || row.get().responseJson == null) {
            return null;
        }
        JsonNode firstRoom;
        try {
            firstRoom = MAPPER.readTree(row.get().responseJson).path("rooms").path(0);
        } catch (Exception e) {
            log.warn("订单供应商响应不可解析 orderId={}，单据房型名留空", orderId, e);
            return null;
        }
        String roomId = firstRoom.path("id").asText(null);
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        return new String[]{roomId, firstRoom.path("bed_group_id").asText(null)};
    }

    /**
     * 按语言取房型名与床型描述。英文供入住单（境外前台不认中文），中文供确认单。
     *
     * <p>反查不到返回 null，单据上相应位置留空——**不做中译英猜测**。
     */
    private ObjectNode roomContent(String propertyId, String roomId, String bedGroupId, String language) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }
        Optional<PropertyContentRepo.PropertySummary> content =
                contentRepo.findById(propertyId, language);
        if (content.isEmpty() || content.get().raw == null) {
            return null;
        }
        JsonNode room = content.get().raw.path("rooms").path(roomId);
        if (room.isMissingNode()) {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("roomName", room.path("name").asText(null));
        if (bedGroupId != null && !bedGroupId.isBlank()) {
            node.put("bedDescription",
                    room.path("bed_groups").path(bedGroupId).path("description").asText(null));
        }
        return node;
    }

    /**
     * 入住单上的酒店联系信息：英文名、电话、英文地址。
     *
     * <p>为什么要单独取一次而不用 bff 查单已带的 {@code propertyContent}：那份是按展示语言
     * （中文）取的，且不含电话。入住单是客人拿到境外酒店前台的凭据——中文名前台对不上，
     * 而客人到不了店时电话是唯一的自救手段。
     *
     * <p>取不到返回 null（该酒店英文静态内容未摄取），入住单上相应位置留空，不猜。
     */
    private ObjectNode hotelContact(String propertyId) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }
        Optional<PropertyContentRepo.PropertySummary> found =
                contentRepo.findById(propertyId, VOUCHER_LANGUAGE);
        if (found.isEmpty()) {
            return null;
        }
        PropertyContentRepo.PropertySummary summary = found.get();
        JsonNode raw = summary.raw;
        ObjectNode node = MAPPER.createObjectNode();
        node.put("nameEn", summary.name);
        if (raw != null) {
            node.put("phone", raw.path("phone").asText(null));
            JsonNode address = raw.path("address");
            node.put("addressLine1", address.path("line_1").asText(null));
            node.put("city", address.path("city").asText(null));
            node.put("postalCode", address.path("postal_code").asText(null));
            node.put("countryCode", address.path("country_code").asText(null));
        }
        return node;
    }

    public JsonNode cancelOrder(String agentId, String orderId, String test) {
        requireOwnership(agentId, orderId);
        return bookingService.cancelOrder(orderId, test);
    }

    public boolean hasAcceptedAgreement(String agentId) {
        return agreementStore.hasAccepted(agentId, props.getAgreement().getVersion());
    }

    private void requireAgreement(String agentId) {
        if (!hasAcceptedAgreement(agentId)) {
            throw new B2bException(403, "请先阅读并接受 Expedia 下游代理协议后再下单");
        }
    }

    /**
     * 不是自己的单一律报 404 而非 403：403 会告诉试探者「这个单号存在」，
     * 而单号是可枚举的。
     */
    private void requireOwnership(String agentId, String orderId) {
        if (!ownerStore.ownedBy(orderId, agentId)) {
            throw new B2bException(404, "订单不存在: " + orderId);
        }
    }

    /** 列表项：够列表页展示与跳详情，不含价格明细与政策快照 */
    private ObjectNode brief(OrderStore.OrderRow row) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("orderId", row.orderId);
        node.put("itineraryId", row.itineraryId);
        node.put("propertyId", row.propertyId);
        node.put("propertyName", row.propertyName);
        node.put("checkin", row.checkin);
        node.put("checkout", row.checkout);
        node.put("occupancy", row.occupancy);
        node.put("travelerName", row.travelerName);
        node.put("status", row.status);
        node.put("createdAt", row.createdAt);
        return node;
    }
}
