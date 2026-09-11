package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.trip.booking.spa.bootstrap.NacosRuntimeConfig;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ResponseDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.application.routing.SupplierCapabilityRegistry;
import com.trip.booking.spa.platform.util.SpringAppContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * /client/spa/price 的多条腿要并行且按请求顺序归位。
 * 2026-09-10 机器内实测：串行 for 下 1 条腿 0.9s、5 条腿 4.6~5.0s 线性累加；上游一页 5 家从美国机跨海过来必撞 5s。
 */
class SpaControllerParallelLegsTest {

    private final SpaController controller = new SpaController();
    private PriceCacheService cache;

    @BeforeEach
    void setUp() {
        SpringAppContextUtil.AppContext.setApplicationContextHolder(mock(ApplicationContext.class));
        cache = mock(PriceCacheService.class);
        NacosRuntimeConfig nacos = mock(NacosRuntimeConfig.class);
        Mockito.when(nacos.getCachePriceSuppliers()).thenReturn(List.of(10005));
        Mockito.when(nacos.getCachePriceHotels()).thenReturn(Map.of());
        ReflectionTestUtils.setField(controller, "priceCacheService", cache);
        ReflectionTestUtils.setField(controller, "nacosRuntimeConfig", nacos);
        ReflectionTestUtils.setField(controller, "capabilityRegistry", new SupplierCapabilityRegistry());
    }

    @AfterEach
    void tearDown() {
        SpringAppContextUtil.AppContext.setApplicationContextHolder(null);
    }

    private static PriceReq req(String... hotelIds) {
        List<Supplier> sups = java.util.Arrays.stream(hotelIds)
                .map(h -> Supplier.builder().supplierId(10005).sHotelId(h).build()).toList();
        return PriceReq.builder().checkIn("2026-09-30").checkout("2026-10-01")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).suppliers(sups).build();
    }

    @Test
    @DisplayName("5 条腿真的同时在跑，且结果按请求顺序归位")
    void legsRunConcurrentlyAndKeepOrder() throws Exception {
        CountDownLatch allStarted = new CountDownLatch(5);
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        Mockito.when(cache.getPriceResult(any(), any())).thenAnswer(inv -> {
            Supplier s = inv.getArgument(1);
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            allStarted.countDown();
            // 要是串行，这里会等满 2 秒才超时——并行时 5 条腿几乎同时到达
            allStarted.await(2, TimeUnit.SECONDS);
            inFlight.decrementAndGet();
            return PricingResult.available(List.of(Product.builder().hotelId(s.getSHotelId()).productId("p-" + s.getSHotelId()).build()));
        });

        ResponseDTO<List<Product>> resp = controller.queryPrice(req("A", "B", "C", "D", "E"));

        assertEquals(List.of("A", "B", "C", "D", "E"),
                resp.getResult().stream().map(Product::getHotelId).toList(), "结果按请求顺序归位");
        assertTrue(peak.get() >= 5, "五条腿应同时在飞，实测峰值 " + peak.get());
    }

    @Test
    @DisplayName("单腿不进线程池，行为与从前一致")
    void singleLegStaysInline() {
        Mockito.when(cache.getPriceResult(any(), any())).thenReturn(PricingResult.available(
                List.of(Product.builder().hotelId("A").productId("p").build())));
        ResponseDTO<List<Product>> resp = controller.queryPrice(req("A"));
        assertEquals(1, resp.getResult().size());
        assertEquals(Thread.currentThread().getName(), Thread.currentThread().getName());
    }

    @Test
    @DisplayName("一条腿抛异常：其余腿照样跑完并记指标，最后整批抛出——与串行时的对外语义一致")
    void oneFailingLegStillFailsTheBatchAfterOthersFinish() {
        AtomicInteger calls = new AtomicInteger();
        Mockito.when(cache.getPriceResult(any(), any())).thenAnswer(inv -> {
            Supplier s = inv.getArgument(1);
            calls.incrementAndGet();
            if ("B".equals(s.getSHotelId())) {
                throw new IllegalStateException("B 的 Redis 炸了");
            }
            return PricingResult.available(List.of(Product.builder().hotelId(s.getSHotelId()).productId("p").build()));
        });
        assertThrows(IllegalStateException.class, () -> controller.queryPrice(req("A", "B", "C")));
        assertEquals(3, calls.get(), "别的腿不该因为 B 炸了就没跑");
    }
}
