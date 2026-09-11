package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaHelper;
import com.trip.booking.spa.platform.observability.RecordLogService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 当天入住拦截闸口的开关（§3.8.5：闸口须有可检索输出、可不发版关闭）。
 *
 * <p>此前这个闸口<b>没有开关</b>：名单硬编码在制品里的 11,681 条 json 中，一旦误拦，
 * 唯一的止血手段是改名单再走一遍完整发布。本组钉住"能关"这件事本身。
 */
class SameDayBlockGateTest {

    private ExpediaProductSyncServiceImpl service;
    private ExpediaPriceService priceService;

    /** 取名单里真实存在的一个 id——用假 id 测不出闸口，只会走到下游 */
    private static String blockedHotelId() {
        assertTrue(!ExpediaHelper.hotelIdList.isEmpty(), "名单为空，本测试失去意义");
        return ExpediaHelper.hotelIdList.get(0);
    }

    @BeforeEach
    void setUp() {
        service = new ExpediaProductSyncServiceImpl();
        priceService = Mockito.mock(ExpediaPriceService.class);
        ReflectionTestUtils.setField(service, "expediaPriceService", priceService);
        ReflectionTestUtils.setField(service, "redisRecordLogServiceImpl", Mockito.mock(RecordLogService.class));
        Mockito.when(priceService.queryPrices(Mockito.any(), Mockito.any()))
                .thenReturn(PricingResult.noInventory());
    }

    private PriceReq sameDayReq(String hotelId) {
        return PriceReq.builder()
                .checkIn(LocalDate.now().toString())
                .checkout(LocalDate.now().plusDays(1).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .suppliers(List.of(Supplier.builder().supplierId(10005).sHotelId(hotelId).build()))
                .build();
    }

    @Test
    @DisplayName("开关打开时拦截，且不打供应商")
    void blocksWhenEnabled() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String hotelId = blockedHotelId();

        PricingResult r = service.querySupplierPrice(sameDayReq(hotelId),
                Supplier.builder().supplierId(10005).sHotelId(hotelId).build());

        assertEquals(PricingOutcome.NO_INVENTORY, r.outcome(),
                "闸口拒绝归入无可售——重试无用，报未能确认只会诱发无谓重试");
        Mockito.verify(priceService, Mockito.never()).queryPrices(Mockito.any(), Mockito.any());
    }

    /**
     * 这条是本次改动的全部意义：以前关不掉，只能改制品重发一次版。
     */
    @Test
    @DisplayName("开关关闭时放行——不发版即可止血")
    void passesThroughWhenDisabled() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", false);
        String hotelId = blockedHotelId();

        service.querySupplierPrice(sameDayReq(hotelId),
                Supplier.builder().supplierId(10005).sHotelId(hotelId).build());

        Mockito.verify(priceService).queryPrices(Mockito.any(), Mockito.any());
    }

    /**
     * 闸口只许看<b>该供应商自己的</b>酒店，不许从请求的 suppliers 列表里取。
     *
     * <p>此前取的是 {@code priceReq.getSuppliers().get(0)}——上游一次问多家时，
     * Expedia 这条腿会拿<b>别家</b>的酒店号去比 Expedia 的名单，永远比不中，名单静默失效。
     * 而紧接着的拦截日志打的又是该供应商的酒店号，看日志发现不了。
     *
     * <p>本组原有三条用例都把 suppliers 构造成与该供应商同一家酒店，两者恰好相等，故照不出这个 bug
     * ——与 {@code PriceCacheService} javadoc 记的是同一种自证陷阱。
     */
    @Test
    @DisplayName("多供应商且 Expedia 不在首位：仍按该供应商的酒店拦截")
    void gateReadsThisLegNotTheFirstSupplier() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String blocked = blockedHotelId();
        PriceReq req = PriceReq.builder()
                .checkIn(LocalDate.now().toString())
                .checkout(LocalDate.now().plusDays(1).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                // 首位是别家（艺龙），Expedia 排第二
                .suppliers(List.of(
                        Supplier.builder().supplierId(10010).sHotelId("ELONG-NOT-IN-LIST").build(),
                        Supplier.builder().supplierId(10005).sHotelId(blocked).build()))
                .build();

        PricingResult r = service.querySupplierPrice(req,
                Supplier.builder().supplierId(10005).sHotelId(blocked).build());

        assertEquals(PricingOutcome.NO_INVENTORY, r.outcome(),
                "闸口读错了供应商：拿首位那家的酒店号比 Expedia 名单，名单等于没有");
        Mockito.verify(priceService, Mockito.never()).queryPrices(Mockito.any(), Mockito.any());
    }

    /**
     * 刷价路径构造的 {@link PriceReq} <b>不带 suppliers</b>（酒店走另一个参数），
     * 闸口若去读那个列表就是 NPE。同 {@code PriceCacheService} javadoc 记的纪律。
     */
    @Test
    @DisplayName("请求不带 suppliers（刷价形状）也不得抛异常")
    void requestWithoutSuppliersDoesNotBlowUp() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String blocked = blockedHotelId();
        PriceReq refreshShaped = PriceReq.builder()
                .checkIn(LocalDate.now().toString())
                .checkout(LocalDate.now().plusDays(1).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .build();

        PricingResult r = service.querySupplierPrice(refreshShaped,
                Supplier.builder().supplierId(10005).sHotelId(blocked).build());

        assertEquals(PricingOutcome.NO_INVENTORY, r.outcome());
    }

    @Test
    @DisplayName("非当天入住不受影响")
    void futureCheckInIsNeverBlocked() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String hotelId = blockedHotelId();
        PriceReq req = PriceReq.builder()
                .checkIn(LocalDate.now().plusDays(3).toString())
                .checkout(LocalDate.now().plusDays(4).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .suppliers(List.of(Supplier.builder().supplierId(10005).sHotelId(hotelId).build()))
                .build();

        service.querySupplierPrice(req, Supplier.builder().supplierId(10005).sHotelId(hotelId).build());

        Mockito.verify(priceService).queryPrices(Mockito.any(), Mockito.any());
    }
}
