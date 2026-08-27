package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
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
 * 验价即刷回写的三态口径必须与查价同源：有货回写产品、下架/明确无货回写空列表
 * （打无货标记清僵尸价 B7）、平台错误不动缓存（F-5.1）。
 */
class FliggyFreshPricesTest {

    private FliggyPriceServiceImpl service;
    private PriceCacheService priceCacheService;

    @BeforeEach
    void setUp() {
        service = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(properties));
        priceCacheService = Mockito.mock(PriceCacheService.class);
        ReflectionTestUtils.setField(service, "priceCacheService", priceCacheService);
    }

    private static CheckPriceReq req() {
        return CheckPriceReq.builder().supplierId(10015).sHotelId("H1")
                .checkIn("2026-09-10").checkOut("2026-09-11")
                .roomNum(1).adultCount(2).childNum(0).childAges(List.of()).build();
    }

    @Test
    @DisplayName("有货 → 回写转换后的产品")
    void sellableWritesProducts() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"data\":{\"request_trace_id\":\"t\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                        + "{\"rate_key\":\"rk\",\"room_id\":\"R1\",\"total_rate\":{\"inclusive\":\"100\",\"currency\":\"USD\"}}]}]}}");
        service.freshPricesToCache(req(), ari);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheService).productToCache(captor.capture(), any(), any());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    @DisplayName("下架 → 回写空列表(无货标记清僵尸价)")
    void delistedWritesEmpty() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"error_response\":{\"code\":15,\"sub_code\":\"F\",\"sub_msg\":\"BizException: hids is empty\"}}");
        service.freshPricesToCache(req(), ari);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheService).productToCache(captor.capture(), any(), any());
        assertEquals(0, captor.getValue().size());
    }

    @Test
    @DisplayName("平台错误(如 session 病) → 不动缓存")
    void platformErrorDoesNotTouchCache() {
        FliggyAriResponse ari = FliggyAriResponse.parse(
                "{\"error_response\":{\"code\":27,\"msg\":\"Invalid session\"}}");
        service.freshPricesToCache(req(), ari);
        verify(priceCacheService, never()).productToCache(any(), any(), any());
    }
}
