package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 缓存读侧的丢弃分支必须真的产生 {@code quote_dropped} 计数（O-4.5），且标签值
 * 走词表：supplier 是枚举名、stage/reason 出自 {@code FunnelStage}/{@code DropReason}。
 *
 * <p>用真 {@link MonitorService} + {@link SimpleMeterRegistry} 断言到「指标名+标签」
 * 这一层——只 mock 掉 Monitor 就验不出 {@code _count} 后缀拼接与标签值大小写这两处
 * 最容易错的地方。反证已做：把 {@code countDropped} 调用注释掉，三条断言全红。
 */
class QuoteDroppedMetricTest {

    private static final String D1 = "2026-09-01";
    private static final String D2 = "2026-09-02";
    private static final String D3 = "2026-09-03";

    private PriceCacheServiceImpl service;
    private com.trip.booking.spa.platform.redis.RedisUtils redisUtils;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        service = new PriceCacheServiceImpl();
        redisUtils = Mockito.mock(com.trip.booking.spa.platform.redis.RedisUtils.class);
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "productAttributeReader",
                Mockito.mock(ProductAttributeReader.class));
        ReflectionTestUtils.setField(service, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(service, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());

        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        // Monitor 的服务是静态注入，不还原会让其他测试悄悄开始计数
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    private static Supplier elong() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    private static PriceReq req(String checkout) {
        return PriceReq.builder().checkIn(D1).checkout(checkout)
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).guestType(0)
                .build();
    }

    /** 让第一个 priceKey 返回一条价格；两晚住期下第二天缺价 → DAY_COUNT_MISMATCH */
    private void firstDayOnly(String priceJson) {
        Mockito.when(redisUtils.hashMapListAndKey(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            return Map.of(keys.get(0), Map.of("PK1", priceJson));
        });
    }

    private double dropped(String reason) {
        return registry.counter("quote_dropped_count",
                "supplier", "ELONG", "stage", "cache_read", "reason", reason).count();
    }

    @Test
    @DisplayName("两晚住期只有一天有价 → day_count_mismatch 计一次")
    void missingDayIsCounted() {
        firstDayOnly("{\"price\":100}");

        service.getPrice(req(D3), elong());

        assertEquals(1.0, dropped("day_count_mismatch"));
    }

    @Test
    @DisplayName("总价为 0 → zero_total_price 计一次")
    void zeroTotalIsCounted() {
        firstDayOnly("{\"price\":0}");

        service.getPrice(req(D2), elong());

        assertEquals(1.0, dropped("zero_total_price"));
    }

    @Test
    @DisplayName("有价无票据详情 → quote_detail_missing 计一次，且不出报")
    void missingQuoteDetailIsCounted() {
        firstDayOnly("{\"price\":100}");
        Mockito.when(redisUtils.get(anyString())).thenReturn("");

        List<?> products = service.getPrice(req(D2), elong());

        assertEquals(0, products.size());
        assertEquals(1.0, dropped("quote_detail_missing"));
    }
}
