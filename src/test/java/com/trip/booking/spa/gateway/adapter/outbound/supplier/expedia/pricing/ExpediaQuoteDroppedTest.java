package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expedia 转换阶段的丢弃分支必须计 {@code quote_dropped}（O-4.5）：rate 在响应里、
 * 但没有本次查询占用档的价——此前这条分支无日志无计数，被过滤的报价无声消失。
 * 标签口径与艺龙同源：supplier 枚举名 / stage=convert / reason 出自 DropReason。
 */
class ExpediaQuoteDroppedTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("rate 缺所查占用档的价 → 弃之且 no_occupancy_pricing 计一次")
    void rateWithoutRequestedOccupancyIsCountedAsDropped() {
        ExpediaPriceServiceImpl service = new ExpediaPriceServiceImpl();
        QueryPriceResponse.Rates rate = new QueryPriceResponse.Rates();
        rate.setOccupancy_pricing(Map.of());
        PriceReq request = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        request.setOccupancies(List.of("2"));
        List<ProductRespDTO> out = new ArrayList<>();

        service.convertRateResp("H1", "大床房", "R1", rate, "hotel_only", out, request);

        assertTrue(out.isEmpty());
        assertEquals(1.0, registry.counter("quote_dropped_count",
                "supplier", "EXPEDIA", "stage", "convert", "reason", "no_occupancy_pricing").count());
    }
}
