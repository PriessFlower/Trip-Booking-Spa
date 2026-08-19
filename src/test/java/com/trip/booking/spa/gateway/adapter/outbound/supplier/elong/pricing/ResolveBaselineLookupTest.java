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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 换票容差基准价的口径钉死(2026-08-19 Owner 质疑时发现的量级错误)。
 *
 * <p>基准价必须走与出价<b>完全相同</b>的 {@link CachePriceService#getPrice} 路径:
 * 客人看到的价是按其查询区间逐日累加的。曾错误地读产品详情缓存的 totalPrice 字段——
 * 那是刷价那一次(任务行区间通常 1 晚)的快照,客人查 3 晚时基准会小一个量级,
 * 容差判断整体失真。
 */
class ResolveBaselineLookupTest {

    private static CheckPriceReq req() {
        return CheckPriceReq.builder()
                .supplierId(10010).sHotelId("61832733").sProductId("P1")
                .checkIn("2026-08-21").checkOut("2026-08-24")   // 3 晚
                .roomNum(1).adultCount(2).childNum(0)
                .build();
    }

    @Test
    void baselineUsesSameDateRangeAsPricing() {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any()))
                .thenReturn(List.of(ProductRespDTO.builder().productId("P1").totalPrice(90000).build()));
        ReflectionTestUtils.setField(service, "cachePriceService", cache);

        Integer baseline = service.lookupTotalPriceFromCache(req());

        assertEquals(90000, baseline, "基准=出价口径的区间总价");
        ArgumentCaptor<PriceReq> pr = ArgumentCaptor.forClass(PriceReq.class);
        ArgumentCaptor<Supplier> sp = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(cache).getPrice(pr.capture(), sp.capture());
        assertEquals("2026-08-21", pr.getValue().getCheckIn(), "必须带客人的入住日");
        assertEquals("2026-08-24", pr.getValue().getCheckout(), "必须带客人的离店日——3 晚不能按 1 晚取基准");
        assertEquals("P1", sp.getValue().getSProductId(), "必须限定到该报价,而非全店最低价");
    }

    /** 查不到基准就不换票——无锚不猜(R-1.6) */
    @Test
    void missingBaselineYieldsNull() {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "cachePriceService", cache);
        assertNull(service.lookupTotalPriceFromCache(req()));
    }

    /** 缓存异常不得打断验价主流程 */
    @Test
    void cacheFailureIsSwallowed() {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        CachePriceService cache = Mockito.mock(CachePriceService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any())).thenThrow(new RuntimeException("redis down"));
        ReflectionTestUtils.setField(service, "cachePriceService", cache);
        assertNull(service.lookupTotalPriceFromCache(req()));
    }
}
