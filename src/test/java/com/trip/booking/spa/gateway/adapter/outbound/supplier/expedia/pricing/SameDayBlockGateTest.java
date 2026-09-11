package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
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
        Mockito.when(priceService.queryPrices(Mockito.any()))
                .thenReturn(PricingResult.noInventory());
    }

    private static PriceQuery sameDayQuery(String hotelId) {
        return PriceQuery.builder()
                .supplierId(10005).supplierHotelId(hotelId)
                .checkIn(LocalDate.now().toString())
                .checkOut(LocalDate.now().plusDays(1).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .build();
    }

    @Test
    @DisplayName("开关打开时拦截，且不打供应商")
    void blocksWhenEnabled() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String hotelId = blockedHotelId();

        PricingResult r = service.querySupplierPrice(sameDayQuery(hotelId));

        assertEquals(PricingOutcome.NO_INVENTORY, r.outcome(),
                "闸口拒绝归入无可售——重试无用，报未能确认只会诱发无谓重试");
        Mockito.verify(priceService, Mockito.never()).queryPrices(Mockito.any());
    }

    /**
     * 这条是本次改动的全部意义：以前关不掉，只能改制品重发一次版。
     */
    @Test
    @DisplayName("开关关闭时放行——不发版即可止血")
    void passesThroughWhenDisabled() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", false);
        String hotelId = blockedHotelId();

        service.querySupplierPrice(sameDayQuery(hotelId));

        Mockito.verify(priceService).queryPrices(Mockito.any());
    }

    /**
     * 闸口只许看该供应商自己的酒店。
     *
     * <p>#237 修的是「取了请求里第一个供应商的酒店号」，而 2026-09-11 的查价解耦之后
     * <b>这个错已经写不出来</b>：{@link PriceQuery} 结构上只装一家供应商的坐标，
     * 不存在「列表里第几个」这回事。本用例钉住这条结构保证——若日后有人往查价指令里
     * 塞回一个供应商列表，它会失败。
     */
    @Test
    @DisplayName("查价指令结构上只带一家供应商，取错家无从写起")
    void queryCarriesExactlyOneSupplier() {
        String blocked = blockedHotelId();
        PriceQuery q = sameDayQuery(blocked);

        assertEquals(blocked, q.supplierHotelId());
        assertTrue(java.util.Arrays.stream(PriceQuery.class.getDeclaredFields())
                        .noneMatch(f -> java.util.List.class.isAssignableFrom(f.getType())
                                && f.getName().toLowerCase().contains("supplier")),
                "PriceQuery 不得带供应商列表：一次请求 × 一家供应商是它的定义");
    }

    @Test
    @DisplayName("非当天入住不受影响")
    void futureCheckInIsNeverBlocked() {
        ReflectionTestUtils.setField(service, "sameDayBlockEnabled", true);
        String hotelId = blockedHotelId();
        PriceQuery req = PriceQuery.builder()
                .checkIn(LocalDate.now().plusDays(3).toString())
                .checkOut(LocalDate.now().plusDays(4).toString())
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .build();

        service.querySupplierPrice(req);

        Mockito.verify(priceService).queryPrices(Mockito.any());
    }
}
