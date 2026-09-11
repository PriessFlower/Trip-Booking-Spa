package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;

import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验价即刷<b>转换</b>的三态口径必须与查价同源：有货给产品、下架/明确无货给空列表
 * （由模板落无货标记清僵尸价 B7）、平台错误给 null（不动缓存，F-5.1）。
 *
 * <p>回写机制（线程池、请求组装、异常兜底、日志与指标）2026-09-07 起归
 * {@code AbstractCheckPriceFlow}，由 {@code CheckPriceFlowTest} 守；本类只管这一家的转换。
 */
class FliggyFreshPricesTest {

    private FliggyPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(properties));
    }

    private static PriceQuery req() {
        PriceQuery r = PriceQuery.builder()
                .supplierId(10015).supplierHotelId("10970375")
                .checkIn("2026-09-10").checkOut("2026-09-11")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        r = r.toBuilder().occupancies(com.trip.booking.spa.gateway.domain.product.Occupancy
                .perRoom(1, 2, 0, List.of())).build();
        return r;
    }

    @Test
    @DisplayName("有货 → 给出转换后的产品")
    void sellableWritesProducts() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"data\":{\"request_trace_id\":\"t\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                        + "{\"rate_key\":\"rk\",\"room_id\":\"R1\",\"total_rate\":{\"inclusive\":\"100\",\"currency\":\"USD\"}}]}]}}");
        assertEquals(1, service.freshProducts(ari, req(), "H1").size());
    }

    @Test
    @DisplayName("下架 → 给空列表(模板落无货标记清僵尸价)")
    void delistedWritesEmpty() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"error_response\":{\"code\":15,\"sub_code\":\"F\",\"sub_msg\":\"BizException: hids is empty\"}}");
        assertEquals(0, service.freshProducts(ari, req(), "H1").size(),
                "空列表=明确无货，模板据此落无货标记清僵尸价");
    }

    @Test
    @DisplayName("平台错误(如 session 病) → 给 null，模板不动缓存")
    void platformErrorDoesNotTouchCache() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"error_response\":{\"code\":27,\"msg\":\"Invalid session\"}}");
        org.junit.jupiter.api.Assertions.assertNull(service.freshProducts(ari, req(), "H1"),
                "null=没问出结果，模板据此不动缓存（F-5.1）");
    }
}
