package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住查价模板的判定纪律：<b>没问出结果时一律回报 INDETERMINATE，绝不说成「无在售」</b>。
 *
 * <p>改造前该模板一律返回 {@code null}，出口再统一成 {@code errorMsg="result is null"}——
 * 供应商明确无房、限流被拒、跨云超时、解析失败，上游收到的是同一句话，无从分辨。
 * 这两者的正确处置相反：无房该劝退旅客，没问出来该稍后重试。
 */
class AbstractProductSyncSupportServiceTest {

    /** 实现方绕过分态返回 null：不得当成无房 */
    @Test
    void reportsIndeterminateWhenImplementationReturnsNull() {
        PricingResult result = new StubService(null).queryPrice(request(), supplier());

        assertNotNull(result, "禁止返回 null——上游无从区分三态");
        assertEquals(PricingOutcome.INDETERMINATE, result.outcome());
        assertTrue(result.products().isEmpty());
    }

    /** 抛异常同样不得当成无房——异常不等于「供应商说没有」 */
    @Test
    void reportsIndeterminateWhenImplementationThrows() {
        PricingResult result = new ThrowingService().queryPrice(request(), supplier());

        assertEquals(PricingOutcome.INDETERMINATE, result.outcome());
    }

    /** 适配层判定的「确定无在售」必须原样透出，不得被模板改判 */
    @Test
    void keepsNoInventoryDecidedByAdapter() {
        PricingResult result = new StubService(PricingResult.noInventory())
                .queryPrice(request(), supplier());

        assertEquals(PricingOutcome.NO_INVENTORY, result.outcome());
    }

    /** 有货照常透出 */
    @Test
    void keepsAvailable() {
        PricingResult result = new StubService(PricingResult.available(List.of(new ProductRespDTO())))
                .queryPrice(request(), supplier());

        assertEquals(PricingOutcome.AVAILABLE, result.outcome());
        assertEquals(1, result.products().size());
    }

    /**
     * 分态不得与事实矛盾：声称有货却给空列表时，纠正为无在售。
     * 否则上游会拿着 AVAILABLE 去展示一个空列表。
     */
    @Test
    void availableWithEmptyListIsCorrectedToNoInventory() {
        assertEquals(PricingOutcome.NO_INVENTORY, PricingResult.available(List.of()).outcome());
        assertEquals(PricingOutcome.NO_INVENTORY, PricingResult.available(null).outcome());
    }

    private static PriceReq request() {
        return PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
    }

    private static Supplier supplier() {
        return Supplier.builder().supplierId(10010).sHotelId("91234567").build();
    }

    private static class StubService extends AbstractProductSyncSupportService {
        private final PricingResult result;

        StubService(PricingResult result) {
            this.result = result;
        }

        @Override
        public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
            return result;
        }
    }

    private static class ThrowingService extends AbstractProductSyncSupportService {
        @Override
        public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
            throw new IllegalStateException("供应商连接超时");
        }
    }
}
