package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaGuestRoom;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaName;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住下单请求的<b>线上字段名与形状</b>（§4.2.3）：Jackson 写错字段名不报错，道旅只会回 -2
 * 「Invalid Request Parameter」或 3005/3011。对照物是官方 booking-confirm 的字段表与
 * cursor 生产被接受的请求（2026-09-13，字段集合完全一致：Header/ReferenceNo/CheckInDate/CheckOutDate/
 * NumOfRooms/GuestList/Contact/ClientReference）。
 */
class DidaBookingRequestShapeTest {

    /** 官方 booking-confirm 请求字段表（2026-09-13 查阅）里的顶层字段——本仓发出的键必须是它的子集 */
    private static final Set<String> OFFICIAL_TOP_LEVEL = Set.of("Header", "ReferenceNo", "CheckInDate", "CheckOutDate",
            "NumOfRooms", "GuestList", "Contact", "CustomerRequest", "ClientReference", "PaymentInfo");

    @Test
    @DisplayName("整单请求：字段名与官方一致，住期/间数/占用从句柄回放，ClientReference=我方单号")
    void requestShapeMatchesOfficialContract() {
        DidaBookingSyncServiceImpl service = service();
        BookingReq req = req("张/三", "李/四", "13800000000", "2026-10-01", "2026-10-03", 2);
        Offer offer = offer(Map.of(
                DidaOfferCredentials.REFERENCE_NO, "18698545882",
                DidaOfferCredentials.HOTEL_ID, "875535",
                DidaOfferCredentials.CHECK_IN, "2026-10-01",
                DidaOfferCredentials.CHECK_OUT, "2026-10-03",
                DidaOfferCredentials.ROOM_NUM, "2",
                DidaOfferCredentials.ADULT_COUNT, "2",
                DidaOfferCredentials.CHILD_AGES, "5",
                DidaOfferCredentials.DECLARED_TOTAL, "212",
                DidaOfferCredentials.CURRENCY, "CNY"));

        JsonNode json = JsonUtils.readTree(JsonUtils.writeObject2Json(service.buildRequest(req, offer)));

        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            keys.add(it.next());
        }
        assertTrue(OFFICIAL_TOP_LEVEL.containsAll(keys), "发出了官方字段表之外的键: " + keys);
        assertFalse(keys.contains("PaymentInfo"), "额度支付不发 PaymentInfo");
        assertFalse(keys.contains("CustomerRequest"), "契约无特殊需求，不发空字符串占位");

        assertEquals("BJ-TEST", json.get("Header").get("ClientID").asText());
        assertEquals("KEY-TEST", json.get("Header").get("LicenseKey").asText());
        assertEquals("18698545882", json.get("ReferenceNo").asText());
        assertEquals("2026-10-01", json.get("CheckInDate").asText());
        assertEquals("2026-10-03", json.get("CheckOutDate").asText());
        assertEquals(2, json.get("NumOfRooms").asInt());
        assertEquals("ORDER-1", json.get("ClientReference").asText());

        // 每间：2 成人 + 1 儿童（占用照句柄铺满，官方：必须与 PriceConfirm 一致）
        assertEquals(2, json.get("GuestList").size());
        JsonNode room1 = json.get("GuestList").get(0);
        assertEquals(1, room1.get("RoomNum").asInt());
        assertEquals(3, room1.get("GuestInfo").size());
        assertEquals("三", room1.get("GuestInfo").get(0).get("Name").get("First").asText());
        assertEquals("张", room1.get("GuestInfo").get(0).get("Name").get("Last").asText());
        assertTrue(room1.get("GuestInfo").get(0).get("IsAdult").asBoolean());
        assertFalse(room1.get("GuestInfo").get(0).has("Age"), "成人 Age 选填，不发 null");
        JsonNode child = room1.get("GuestInfo").get(2);
        assertFalse(child.get("IsAdult").asBoolean());
        assertEquals(5, child.get("Age").asInt(), "儿童 Age 必填");

        JsonNode contact = json.get("Contact");
        assertEquals("四", contact.get("Name").get("First").asText());
        assertEquals("李", contact.get("Name").get("Last").asText());
        assertEquals("13800000000", contact.get("Phone").asText());
        assertEquals("ops@example.test", contact.get("Email").asText());
    }

    @Test
    @DisplayName("姓名不够人数时循环复用：两间×1 成人、只给一位入住人 → 两间同名")
    void guestNamesCycleWhenShort() {
        List<DidaGuestRoom> rooms = DidaBookingSyncServiceImpl.buildGuestList("王/五", 2, 1, List.of());
        assertEquals(2, rooms.size());
        assertEquals("五", rooms.get(0).getGuestInfo().get(0).getName().getFirst());
        assertEquals("五", rooms.get(1).getGuestInfo().get(0).getName().getFirst());
        // 三位入住人、一间两成人：前两位进第一间
        rooms = DidaBookingSyncServiceImpl.buildGuestList("甲/一、乙/二，丙/三", 1, 2, List.of());
        assertEquals(2, rooms.get(0).getGuestInfo().size());
        assertEquals("乙", rooms.get(0).getGuestInfo().get(1).getName().getLast());
    }

    @Test
    @DisplayName("姓名拆分：姓/名 > 空格分隔（首段为姓）> 中文首字为姓 > 单字同填")
    void nameSplitRules() {
        assertName("三", "张", DidaBookingSyncServiceImpl.splitName("张/三"));
        assertName("Xianen", "Wang", DidaBookingSyncServiceImpl.splitName("Wang Xianen"));
        assertName("小明", "王", DidaBookingSyncServiceImpl.splitName("王小明"));
        assertName("c", "c", DidaBookingSyncServiceImpl.splitName("c"));
        assertName("Shiyao", "Tang", DidaBookingSyncServiceImpl.splitName(" Tang/Shiyao "));
    }

    @Test
    @DisplayName("句柄里的儿童年龄 CSV：空串=无儿童；坏值跳过不抛（建单不可重试，不能被解析炸掉）")
    void childAgesParse() {
        assertTrue(DidaBookingSyncServiceImpl.parseAges("").isEmpty());
        assertTrue(DidaBookingSyncServiceImpl.parseAges(null).isEmpty());
        assertEquals(List.of(3, 7), DidaBookingSyncServiceImpl.parseAges("3,7"));
        assertEquals(List.of(3), DidaBookingSyncServiceImpl.parseAges("3,x"));
    }

    private static void assertName(String first, String last, DidaName name) {
        assertEquals(first, name.getFirst());
        assertEquals(last, name.getLast());
    }

    static DidaBookingSyncServiceImpl service() {
        DidaProperties props = new DidaProperties();
        props.setClientId("BJ-TEST");
        props.setLicenseKey("KEY-TEST");
        props.setBookingContactEmail("ops@example.test");
        props.setBookingEnabled(true);
        DidaBookingSyncServiceImpl service = new DidaBookingSyncServiceImpl();
        ReflectionTestUtils.setField(service, "properties", props);
        return service;
    }

    static BookingReq req(String person, String contact, String phone, String in, String out, int rooms) {
        return BookingReq.builder().supplierId(10020).orderId("ORDER-1").personName(person).contactName(contact)
                .contactPhone(phone).checkIn(in).checkOut(out).roomNum(rooms).totalPrice(21200).settlePrice(21200)
                .offerId("of_test").build();
    }

    static Offer offer(Map<String, String> credentials) {
        return Offer.builder().supplierId(10020).credentials(new HashMap<>(credentials))
                .expiresAt(System.currentTimeMillis() + 60_000).build();
    }
}
