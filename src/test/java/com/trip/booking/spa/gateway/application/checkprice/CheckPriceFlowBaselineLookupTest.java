package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 换票容差基准价的口径钉死（原钉在艺龙实现上，随 resolve 上提到模板后改钉模板；
 * 三家共用这一条反查，任何一家改口径都在这里红）。
 *
 * <p><b>① 区间</b>（2026-08-19）：基准价必须走与出价<b>完全相同</b>的
 * {@link PriceCacheService#getPrice} 路径——客人看到的价是按其查询区间逐日累加的，
 * 读刷价快照里的单晚 totalPrice 会小一个量级。
 *
 * <p><b>② 字段</b>（2026-08-20）：必须按 <b>productKey</b> 限定，不是 sProductId。
 * 缓存字段是 productKey，按报价码找恒 miss——上游未携展示价时永远取不到基准，resolve 静默不换票。
 *
 * <p><b>③ 供应商</b>：反查按实现方申报的 supplier 限定，模板不假定任何一家。
 */
class CheckPriceFlowBaselineLookupTest {

    private static final String PRODUCT_KEY = "d".repeat(64);

    private static CheckPriceCommand req() {
        return CheckPriceCommand.builder()
                .supplierId(10010).supplierHotelId("61832733").supplierProductId("P1").productKey(PRODUCT_KEY)
                .checkIn("2026-08-21").checkOut("2026-08-24")   // 3 晚
                .roomNum(1).adultCount(2).childNum(0)
                .build();
    }

    private static AbstractCheckPriceFlow<?, ?> flowWith(PriceCacheService cache, SupplierSourceEnum supplier) {
        AbstractCheckPriceFlow<Object, Object> flow = new AbstractCheckPriceFlow<>() {
            @Override
            protected SupplierSourceEnum supplier() {
                return supplier;
            }

            @Override
            protected ResolveProperties resolveProperties() {
                return null;
            }

            @Override
            protected LiveStock<Object> fetchLiveStock(CheckPriceCommand request, String salesEnvironment) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Object findByToken(Object stock, CheckPriceCommand request) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected List<ResolveCandidate<Object>> resolveCandidates(Object stock, CheckPriceCommand request) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected String tokenOf(Object candidate) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected CheckPriceResult availabilityOnlyResp(Object candidate, Object stock, CheckPriceCommand request) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected CheckPriceResult validate(Object candidate, Object stock, CheckPriceCommand request) {
                throw new UnsupportedOperationException();
            }
        };
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);
        return flow;
    }

    private static PriceCacheService cacheReturning(List<Product> products) {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any())).thenReturn(products);
        return cache;
    }

    @Test
    void baselineUsesSameDateRangeAsPricing() {
        PriceCacheService cache = cacheReturning(List.of(Product.builder().productId("P1").totalPrice(90000).build()));

        Integer baseline = flowWith(cache, SupplierSourceEnum.ELONG).lookupTotalPriceFromCache(req());

        assertEquals(90000, baseline, "基准=出价口径的区间总价");
        ArgumentCaptor<PriceQuery> pr = ArgumentCaptor.forClass(PriceQuery.class);
        Mockito.verify(cache).getPrice(pr.capture(), Mockito.any());
        assertEquals("2026-08-21", pr.getValue().checkIn(), "必须带客人的入住日");
        assertEquals("2026-08-24", pr.getValue().checkOut(), "必须带客人的离店日——3 晚不能按 1 晚取基准");
    }

    /** 把断言改回 sProductId 即可复现 2026-08-20 的那半个改名 */
    @Test
    void baselineIsLookedUpByProductKeyNotByQuoteCode() {
        PriceCacheService cache = cacheReturning(List.of(Product.builder().productId("P1").totalPrice(90000).build()));

        CheckPriceCommand request = req();
        flowWith(cache, SupplierSourceEnum.ELONG).lookupTotalPriceFromCache(request);

        ArgumentCaptor<PriceQuery> pq = ArgumentCaptor.forClass(PriceQuery.class);
        ArgumentCaptor<String> field = ArgumentCaptor.forClass(String.class);
        Mockito.verify(cache).getPrice(pq.capture(), field.capture());

        assertEquals(PRODUCT_KEY, field.getValue(), "缓存字段是 productKey——写入侧就是按它写的");
        assertNotEquals(request.supplierProductId(), field.getValue(),
                "拿易腐报价码当缓存字段就是恒 miss，只是miss得很安静");
        assertNull(pq.getValue().supplierProductId(),
                "限定条件只能有一个来源；sProductId 不再兼任缓存字段，避免两端各拼一次");
    }

    /** 反查按实现方申报的供应商限定——飞猪的基准不能去艺龙的缓存里找 */
    @Test
    void baselineIsScopedToTheDeclaredSupplier() {
        PriceCacheService cache = cacheReturning(List.of(Product.builder().productId("P1").totalPrice(90000).build()));

        flowWith(cache, SupplierSourceEnum.FLIGGY).lookupTotalPriceFromCache(req());

        // 供应商坐标现在就在指令里，不再是并排的第二个参数
        ArgumentCaptor<PriceQuery> pq = ArgumentCaptor.forClass(PriceQuery.class);
        Mockito.verify(cache).getPrice(pq.capture(), Mockito.any());
        assertEquals(SupplierSourceEnum.FLIGGY.getCode(), pq.getValue().supplierId());
        assertEquals("61832733", pq.getValue().supplierHotelId());
    }

    /** 查不到基准就不换票——无锚不猜(R-1.6) */
    @Test
    void missingBaselineYieldsNull() {
        assertNull(flowWith(cacheReturning(List.of()), SupplierSourceEnum.ELONG).lookupTotalPriceFromCache(req()));
    }

    /** 缓存异常不得打断验价主流程 */
    @Test
    void cacheFailureIsSwallowed() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("redis down"));
        assertNull(flowWith(cache, SupplierSourceEnum.ELONG).lookupTotalPriceFromCache(req()));
    }
}
