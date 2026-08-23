package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住多间验价的申报口径：{@code TotalPrice = ΣDayPrice.Price × NumberOfRooms}。
 *
 * <p><b>为什么必须钉</b>：这是艺龙 H001188 的校验恒等式（hotel.data.validate 与
 * hotel.order.create 文档原文「DayPriceList节点里每个DayPrice里的Price之和 *
 * NumberOfRooms = TotalPrice」）。2026-08-23 高德 2 间真单（26082320295835a66d8b13dd）
 * 因 TotalPrice 漏乘间数被拒 {@code H001188|每日价传参异常}，整单失败。
 *
 * <p>断言读<b>序列化后的原始 JSON</b> 而非 getter：要锁的是发给艺龙的 wire 报文，
 * 字段改名/注解漂移时 getter 断言会跟着漂而不变红。
 */
class ElongMultiRoomDeclaredTotalTest {

    private static JsonNode wireJson(Integer roomNum) throws Exception {
        CheckPriceReq req = CheckPriceReq.builder().supplierId(10010).sHotelId("61540701")
                .sProductId("61588914A32Atest").checkIn("2026-08-23").checkOut("2026-08-25")
                .roomNum(roomNum).adultCount(1).build();
        ElongRatePlan plan = new ElongRatePlan();
        plan.setHotelCode("40673708");
        plan.setRatePlanId(659021573L);
        plan.setRoomTypeId("12568080");
        plan.setLittleMajiaId("majia-test");
        plan.setGoodsUniqId("61588914A32Atest");
        plan.setShopperProductId("shopper-test");
        plan.setSubSupplierId("5155046");
        plan.setSupplierId("835");
        List<ElongDataValidateRequest.DayPrice> dayPrices = List.of(
                ElongDataValidateRequest.DayPrice.builder().date("2026-08-23")
                        .price(new BigDecimal("405.50")).minRate(new BigDecimal("361.97")).build(),
                ElongDataValidateRequest.DayPrice.builder().date("2026-08-24")
                        .price(new BigDecimal("433.00")).minRate(new BigDecimal("386.53")).build());
        ElongDataValidateRequest built =
                ElongPriceServiceImpl.buildValidateRequest(req, "61540701", plan, dayPrices);
        return JsonUtils.readTree(JsonUtils.writeObject2Json(built));
    }

    @Test
    @DisplayName("2 间：TotalPrice=Σ每日价×2，DayPriceList 保持单间口径")
    void twoRoomsMultipliesDeclaredTotalOnly() throws Exception {
        JsonNode json = wireJson(2);

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(2);
        // Σ(405.50+433.00)=838.50，×2=1677.00——H001188 恒等式的右边
        assertThat(json.get("TotalPrice").decimalValue())
                .isEqualByComparingTo(new BigDecimal("1677.00"));
        // 每日价是单间口径，禁止跟着乘：乘了恒等式两边一起翻倍，且部分退款金额会对不上
        assertThat(json.get("DayPriceList")).hasSize(2);
        assertThat(json.get("DayPriceList").get(0).get("Price").decimalValue())
                .isEqualByComparingTo(new BigDecimal("405.50"));
        assertThat(json.get("DayPriceList").get(1).get("Price").decimalValue())
                .isEqualByComparingTo(new BigDecimal("433.00"));
    }

    @Test
    @DisplayName("1 间：口径与修复前完全一致（存量主流量不受影响）")
    void singleRoomUnchanged() throws Exception {
        JsonNode json = wireJson(1);

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(1);
        assertThat(json.get("TotalPrice").decimalValue())
                .isEqualByComparingTo(new BigDecimal("838.50"));
    }

    @Test
    @DisplayName("间数非法（<1）按 1 间：与下单侧 buildRequest 同规")
    void invalidRoomNumDefaultsToOne() throws Exception {
        JsonNode json = wireJson(0);

        assertThat(json.get("NumberOfRooms").asInt()).isEqualTo(1);
        assertThat(json.get("TotalPrice").decimalValue())
                .isEqualByComparingTo(new BigDecimal("838.50"));
    }
}
