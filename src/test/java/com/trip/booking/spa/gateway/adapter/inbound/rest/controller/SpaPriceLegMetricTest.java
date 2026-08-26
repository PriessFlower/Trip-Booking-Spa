package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.bootstrap.NacosRuntimeConfig;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 * 腿的词表必须穷尽（O-3.3）：正常腿按分态计，<b>异常出去的腿计 error</b>——不计的话
 * sum(腿) 少于真实腿数，出报率分母偏小、算出来偏高。反证已实跑：去掉 catch 里的
 * recordFailedLeg，errorLeg 断言红。
 */
class SpaPriceLegMetricTest {

    private SpaController controller;
    private PriceCacheService priceCacheService;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        controller = new SpaController();
        priceCacheService = Mockito.mock(PriceCacheService.class);
        NacosRuntimeConfig config = Mockito.mock(NacosRuntimeConfig.class);
        // 10010 配置为走缓存、全量酒店
        Mockito.when(config.getCachePriceSuppliers()).thenReturn(List.of(10010));
        Mockito.when(config.getCachePriceHotels()).thenReturn(Map.of());
        ReflectionTestUtils.setField(controller, "priceCacheService", priceCacheService);
        ReflectionTestUtils.setField(controller, "nacosRuntimeConfig", config);

        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    private static PriceReq req() {
        return PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .suppliers(List.of(Supplier.builder().supplierId(10010).sHotelId("H1").build()))
                .build();
    }

    private double leg(String source, String outcome) {
        return registry.counter("spa_price_leg_count",
                "supplier", "ELONG", "source", source, "outcome", outcome).count();
    }

    @Test
    @DisplayName("缓存腿正常分态 → outcome=no_inventory 计一次")
    void normalLegIsCounted() {
        Mockito.when(priceCacheService.getPriceResult(any(), any()))
                .thenReturn(PricingResult.noInventory());

        controller.queryPrice(req());

        assertEquals(1.0, leg("cache", "no_inventory"));
    }

    @Test
    @DisplayName("缓存读抛异常 → outcome=error 计一次，异常照常抛出")
    void errorLegIsCounted() {
        Mockito.when(priceCacheService.getPriceResult(any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThrows(IllegalStateException.class, () -> controller.queryPrice(req()));

        assertEquals(1.0, leg("cache", "error"));
    }
}
