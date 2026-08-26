package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 查价转换：从原始 JSON 一路到 ProductRespDTO（读原始报文，不 new 中间对象）。
 * 钉三件事：价格单位分原样透传+币种自带；身份与票据分列两字段（productKey ≠ rate_key）；
 * 丢弃分支必计 quote_dropped（O-4.5，O45 守护要求本包引用它）。
 */
class FliggyConvertTest {

    private FliggyPriceServiceImpl service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        service = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(properties));

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
        PriceReq req = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(List.of("2"));
        return req;
    }

    @Test
    @DisplayName("好报价出报:分价原样、币种自带、身份与 rate_key 分列;缺 rate_key 弃之且计数")
    void convertsGoodRateAndCountsDropped() {
        String raw = "{\"xhotel_distribution_ari_availability_response\":{\"data\":{"
                + "\"request_trace_id\":\"t1\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                + "{\"rate_key\":\"rk-1\",\"room_id\":\"R1\",\"room_name\":\"大床房\","
                + "\"total_rate\":{\"inclusive\":\"25800\",\"exclusive\":\"23000\",\"currency\":\"USD\"},"
                + "\"meals\":{\"type\":1,\"number\":2}},"
                + "{\"room_id\":\"R2\",\"total_rate\":{\"inclusive\":\"100\",\"currency\":\"USD\"}}"
                + "]}]}}}";
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);

        List<ProductRespDTO> products = service.convertRates(resp.rates(), req(), "H1");

        assertEquals(1, products.size());
        ProductRespDTO p = products.get(0);
        assertEquals("rk-1", p.getProductId());
        assertNotNull(p.getProductKey());
        assertEquals(25800, p.getTotalPrice());
        assertEquals(23000, p.getRoomTotalPrice());
        assertEquals("USD", p.getCurrencyType());
        assertEquals(10015, p.getSupplierId());
        assertEquals(2, p.getMeal().count);

        assertEquals(1.0, registry.counter("quote_dropped_count", "supplier", "FLIGGY",
                "stage", "convert", "reason", "no_session_credentials").count());
    }
}
