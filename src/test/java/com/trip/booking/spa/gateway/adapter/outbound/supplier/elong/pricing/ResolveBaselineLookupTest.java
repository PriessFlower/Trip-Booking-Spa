package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.CachePriceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 换票容差基准价的两条口径钉死。
 *
 * <p><b>① 区间</b>（2026-08-19 Owner 质疑时发现的量级错误）：基准价必须走与出价
 * <b>完全相同</b>的 {@link CachePriceService#getPrice} 路径——客人看到的价是按其查询区间
 * 逐日累加的。曾错误地读产品详情缓存的 totalPrice 字段，那是刷价那一次（任务行区间通常
 * 1 晚）的快照，客人查 3 晚时基准会小一个量级。
 *
 * <p><b>② 字段</b>（2026-08-20 发现）：必须按 <b>productKey</b> 限定，不是 sProductId。
 * 缓存字段自 0853d11 起是 productKey，而这条反查还按报价码找，恒 miss——上游未携展示价时
 * 就永远取不到基准，resolve 静默不换票，日志只有一条 warn。改名只改了写入侧、漏了读取侧，
 * 正是「两端各自拼 key」这个结构的必然产物。
 */
class ResolveBaselineLookupTest {

    private static final String PRODUCT_KEY = "d".repeat(64);

    private static CheckPriceReq req() {
        return CheckPriceReq.builder()
                .supplierId(10010).sHotelId("61832733").sProductId("P1").productKey(PRODUCT_KEY)
                .checkIn("2026-08-21").checkOut("2026-08-24")   // 3 晚
                .roomNum(1).adultCount(2).childNum(0)
                .build();
    }

    private static ElongPriceServiceImpl serviceWith(CachePriceService cache) {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        ReflectionTestUtils.setField(service, "cachePriceService", cache);
        return service;
    }

    @Test
    void baselineUsesSameDateRangeAsPricing() {
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(ProductRespDTO.builder().productId("P1").totalPrice(90000).build()));

        Integer baseline = serviceWith(cache).lookupTotalPriceFromCache(req());

        assertEquals(90000, baseline, "基准=出价口径的区间总价");
        ArgumentCaptor<PriceReq> pr = ArgumentCaptor.forClass(PriceReq.class);
        Mockito.verify(cache).getPrice(pr.capture(), Mockito.any(), Mockito.any());
        assertEquals("2026-08-21", pr.getValue().getCheckIn(), "必须带客人的入住日");
        assertEquals("2026-08-24", pr.getValue().getCheckout(), "必须带客人的离店日——3 晚不能按 1 晚取基准");
    }

    /**
     * 必须用 productKey 作缓存字段限定该条报价。用 sProductId 会恒 miss——
     * 把断言改回 sProductId 即可复现 2026-08-20 的那半个改名。
     */
    @Test
    void baselineIsLookedUpByProductKeyNotByQuoteCode() {
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(ProductRespDTO.builder().productId("P1").totalPrice(90000).build()));

        CheckPriceReq request = req();
        serviceWith(cache).lookupTotalPriceFromCache(request);

        ArgumentCaptor<String> field = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> sp = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(cache).getPrice(Mockito.any(), sp.capture(), field.capture());

        assertEquals(PRODUCT_KEY, field.getValue(), "缓存字段是 productKey——写入侧就是按它写的");
        assertNotEquals(request.getSProductId(), field.getValue(),
                "拿易腐报价码当缓存字段就是恒 miss，只是miss得很安静");
        assertNull(sp.getValue().getSProductId(),
                "限定条件只能有一个来源；sProductId 不再兼任缓存字段，避免两端各拼一次");
    }

    /** 查不到基准就不换票——无锚不猜(R-1.6) */
    @Test
    void missingBaselineYieldsNull() {
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(List.of());
        assertNull(serviceWith(cache).lookupTotalPriceFromCache(req()));
    }

    /** 缓存异常不得打断验价主流程 */
    @Test
    void cacheFailureIsSwallowed() {
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("redis down"));
        assertNull(serviceWith(cache).lookupTotalPriceFromCache(req()));
    }
}
