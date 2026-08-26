package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code pricing_supplier_query} 由查价模板统一打（O-4.3：新接一家自动具备，
 * 不再依赖各家实现记得埋）。此前它埋在 Expedia 实现里五处、艺龙零处——盘上
 * 艺龙的查价量恒为 0，与「没流量」无从区分。
 *
 * <p>映射：AVAILABLE→quoted、NO_INVENTORY→no_inventory、INDETERMINATE→error
 * （含实现返回 null 与抛异常两条兜底路）。supplier 未知时不打——不虚构标签值。
 */
class PricingQueryMetricTest {

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

    private static AbstractProductSyncSupportService returning(PricingResult result) {
        return new AbstractProductSyncSupportService() {
            @Override
            public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
                return result;
            }
        };
    }

    private static Supplier elong() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    private double counted(String status) {
        return registry.counter("pricing_supplier_query_count",
                "supplier", "ELONG", "status", status).count();
    }

    @Test
    @DisplayName("三分态各映射一个终态，一次调用记一次")
    void outcomesMapToStatuses() {
        PriceReq req = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();

        returning(PricingResult.indeterminate()).queryPrice(req, elong());
        assertEquals(1.0, counted("error"));

        returning(PricingResult.noInventory()).queryPrice(req, elong());
        assertEquals(1.0, counted("no_inventory"));

        // available 需要非空产品列表——PricingResult.available 对空列表会纠正为 no_inventory
        returning(new PricingResult(
                com.trip.booking.spa.gateway.domain.booking.PricingOutcome.AVAILABLE,
                List.of())).queryPrice(req, elong());
        assertEquals(1.0, counted("quoted"));
    }

    @Test
    @DisplayName("实现抛异常/返回 null 的兜底路也计入 error——兜底不可无声")
    void fallbackPathsAreCounted() {
        PriceReq req = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();

        returning(null).queryPrice(req, elong());
        new AbstractProductSyncSupportService() {
            @Override
            public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
                throw new IllegalStateException("boom");
            }
        }.queryPrice(req, elong());

        assertEquals(2.0, counted("error"));
    }

    @Test
    @DisplayName("supplier 编码未知时不打指标——不虚构标签值")
    void unknownSupplierIsNotRecorded() {
        PriceReq req = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();

        returning(PricingResult.indeterminate())
                .queryPrice(req, Supplier.builder().supplierId(99999).build());

        assertEquals(0, registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("pricing_supplier_query")).count());
    }
}
