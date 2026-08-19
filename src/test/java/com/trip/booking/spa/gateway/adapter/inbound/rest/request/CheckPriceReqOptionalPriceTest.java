package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * seenPrice(客人所见价,旧名 totalPrice)必须可缺省(2026-08-19 回归钉)。
 *
 * <p>它曾带 Lombok {@code @NonNull}:配合 {@code @Builder} 会在反序列化时抛 NPE,
 * Spring 转 HTTP 400。而接入方未必持有价格——cursor 的验价请求 DTO 就没有价格字段,
 * 于是 spa# 票据的验价被整片打成 400。网关是报价的权威,容差基准价它自己缓存里就有。
 */
class CheckPriceReqOptionalPriceTest {

    @Test
    void buildsWithoutSeenPrice() {
        CheckPriceReq req = assertDoesNotThrow(() -> CheckPriceReq.builder()
                .supplierId(10010)
                .sHotelId("61832733")
                .sProductId("62022758A21A71332055A0A")
                .checkIn("2026-08-21").checkOut("2026-08-22")
                .roomNum(1)
                .build(), "缺 seenPrice 不得抛——否则上游收到 400 而非三态");
        assertNull(req.getSeenPrice());
    }

    /**
     * 旧名 totalPrice 必须仍能收下——改名不得打断存量调用方(@JsonAlias 兼容)。
     */
    @Test
    void legacyFieldNameStillAccepted() throws Exception {
        String json = "{\"supplierId\":10010,\"sHotelId\":\"H\",\"checkIn\":\"2026-08-21\","
                + "\"checkOut\":\"2026-08-22\",\"roomNum\":1,\"totalPrice\":90000}";
        // 与生产一致:CheckPriceReq 无无参构造器,靠 ParameterNamesModule 走全参构造器反序列化
        // (Spring Boot 默认启用 + 编译带 -parameters)。裸 ObjectMapper 测不出真实行为。
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule());
        CheckPriceReq req = mapper.readValue(json, CheckPriceReq.class);
        org.junit.jupiter.api.Assertions.assertEquals(90000, req.getSeenPrice(),
                "老调用方仍传 totalPrice,必须映射到 seenPrice");
    }

    /** 其余必填项仍受保护,避免本次放宽把校验一起放没了 */
    @Test
    void otherRequiredFieldsStillGuarded() {
        assertThrows(NullPointerException.class,
                () -> CheckPriceReq.builder().sHotelId("H").checkIn("2026-08-21")
                        .checkOut("2026-08-22").roomNum(1).build(),
                "supplierId 仍必填");
    }
}
