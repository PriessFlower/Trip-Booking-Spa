package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 曝光档验价（{@link VerifyLevel#AVAILABILITY}）只答"还在售"，不打 validate。
 *
 * <p>为什么必须有这档：飞猪 validate 生产实测 1,833ms（2026-09-02），渠道曝光核价的
 * 单供应商预算 1.5s，超时即被兜底成「不可预订」——高德实测 `SUPPLIER_BUDGET_TIMEOUT`
 * RT 2312ms，报价在列表页被整条抹掉。艺龙 2026-08 就踩过同一个坑并留了同款注释。
 */
class FliggyAvailabilityTierTest {

    private FliggyPriceServiceImpl service;
    private JsonNode rate;

    @BeforeEach
    void setUp() throws Exception {
        service = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(properties));

        String raw = Files.readString(Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        rate = FliggyAriResponse.parse(raw).rates().get(0);
    }

    private CheckPriceReq req(int rooms) {
        return CheckPriceReq.builder()
                .supplierId(10015)
                .sHotelId("50363404")
                .checkIn("2026-09-10")
                .checkOut("2026-09-11")
                .roomNum(rooms)
                .adultCount(2)
                .childNum(0)
                .verifyLevel(VerifyLevel.AVAILABILITY)
                .build();
    }

    @Test
    @DisplayName("真实 ari 报价 → 曝光档：AVAILABLE + 带价带币种带退改，且不签句柄")
    void availabilityTierAnswersFromFreshAriWithoutHandle() {
        CheckPriceRespDTO resp = service.availabilityOnlyResp(req(1), rate);

        assertEquals(CheckPriceOutcome.AVAILABLE, resp.getOutcome());
        assertEquals("USD", resp.getCurrencyType(), "币种必须原样带出——上游按它换汇，缺了就是 7 倍资损");
        assertEquals(10524, resp.getSalePrice(), "整单价取 total_rate.inclusive（单间×1）");
        assertNull(resp.getOfferId(), "曝光档不签句柄：create_key 由 validate 签发，此档没调它");
        assertNull(resp.getOfferTtlSeconds());
        assertFalse(resp.getCancelPolicy().isEmpty(), "退改条款照带（展示口径，与 BOOKABLE 档同一份响应）");
        assertEquals(1, resp.getPriceInfos().size(), "住 1 晚=1 条逐日价");
    }

    @Test
    @DisplayName("多间房：曝光档的整单价必须乘间数，与 BOOKABLE 档口径一致")
    void availabilityTierMultipliesByRooms() {
        assertEquals(10524 * 2, service.availabilityOnlyResp(req(2), rate).getSalePrice());
    }

    @Test
    @DisplayName("缺币种即不确定——不许拿一个没有币种的数字冒充可售")
    void missingCurrencyIsIndeterminate() {
        com.fasterxml.jackson.databind.node.ObjectNode stripped = rate.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) stripped.get("total_rate")).remove("currency");

        CheckPriceRespDTO resp = service.availabilityOnlyResp(req(1), stripped);

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
        assertTrue(resp.getMessage().contains("币种"));
    }
}
