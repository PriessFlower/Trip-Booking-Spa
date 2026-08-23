package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderCreateRequest;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住下单报文的间数来源：NumberOfRooms 取验价句柄的 {@code roomNum}，与句柄里的
 * 申报总价（TotalPrice=Σ每日价×间数）同源——两者取自不同来源就可能凑不出 H001188
 * 的恒等式。旧句柄（改动前签发、仍在 TTL 内）无该键，回落上游间数。
 *
 * <p>断言读序列化后的原始 JSON：锁 wire 报文，不锁 getter。
 */
class ElongBookingRoomNumFromOfferTest {

    private static JsonNode wireJson(int reqRooms, String offerRooms) throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(ElongOfferCredentials.HOTEL_ID, "61540701");
        credentials.put(ElongOfferCredentials.HOTEL_CODE, "40673708");
        credentials.put(ElongOfferCredentials.ROOM_TYPE_ID, "12568080");
        credentials.put(ElongOfferCredentials.RATE_PLAN_ID, "659021573");
        credentials.put(ElongOfferCredentials.GOODS_UNIQ_ID, "61588914A32Atest");
        credentials.put(ElongOfferCredentials.LITTLE_MAJIA_ID, "majia-test");
        credentials.put(ElongOfferCredentials.SUPPLIER_ID, "835");
        credentials.put(ElongOfferCredentials.SUB_SUPPLIER_ID, "5155046");
        credentials.put(ElongOfferCredentials.SHOPPER_PRODUCT_ID, "shopper-test");
        credentials.put(ElongOfferCredentials.DECLARED_TOTAL, "1677.00");
        credentials.put(ElongOfferCredentials.DAY_PRICE_LIST,
                "[{\"Date\":\"2026-08-23\",\"Price\":405.50,\"MinRate\":361.97},"
                        + "{\"Date\":\"2026-08-24\",\"Price\":433.00,\"MinRate\":386.53}]");
        credentials.put(ElongOfferCredentials.CHECK_IN, "2026-08-23");
        credentials.put(ElongOfferCredentials.CHECK_OUT, "2026-08-25");
        credentials.put(ElongOfferCredentials.ADULT_COUNT, "1");
        if (offerRooms != null) {
            credentials.put(ElongOfferCredentials.ROOM_NUM, offerRooms);
        }
        Offer offer = Offer.builder().supplierId(10010).credentials(credentials).build();
        BookingReq req = BookingReq.builder().supplierId(10010).orderId("26082320295835a66d8b13dd")
                .personName("luo/fang、ou/kunqiong").contactName("luo/fang").contactPhone("13688341880")
                .checkIn("2026-08-23").checkOut("2026-08-25").roomNum(reqRooms)
                .totalPrice(167700).settlePrice(167700).build();

        ElongBookingSyncServiceImpl service = new ElongBookingSyncServiceImpl();
        Field propsField = ElongBookingSyncServiceImpl.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        propsField.set(service, new ElongProperties());
        Method m = ElongBookingSyncServiceImpl.class.getDeclaredMethod("buildRequest",
                BookingReq.class, Offer.class);
        m.setAccessible(true);
        ElongOrderCreateRequest built = (ElongOrderCreateRequest) m.invoke(service, req, offer);
        return JsonUtils.readTree(JsonUtils.writeObject2Json(built));
    }

    @Test
    @DisplayName("句柄有 roomNum：NumberOfRooms 与 OrderRooms 数取句柄值")
    void roomsComeFromOffer() throws Exception {
        JsonNode json = wireJson(2, "2");

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(2);
        assertThat(json.get("OrderRooms")).hasSize(2);
        // 申报总价原样回放，与句柄间数构成 H001188 恒等式：Σ(405.50+433.00)×2=1677.00
        assertThat(json.get("TotalPrice").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("1677.00"));
    }

    @Test
    @DisplayName("上游间数与句柄不一致：以句柄为准（价与间数必须同源）")
    void offerWinsOnMismatch() throws Exception {
        JsonNode json = wireJson(1, "2");

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(2);
        assertThat(json.get("OrderRooms")).hasSize(2);
    }

    @Test
    @DisplayName("旧句柄无 roomNum：回落上游间数（存量 TTL 内句柄兼容）")
    void fallsBackToRequestForLegacyOffers() throws Exception {
        JsonNode json = wireJson(2, null);

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(2);
        assertThat(json.get("OrderRooms")).hasSize(2);
    }
}
