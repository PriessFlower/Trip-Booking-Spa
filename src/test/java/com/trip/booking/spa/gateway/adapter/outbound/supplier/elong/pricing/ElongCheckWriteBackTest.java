package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.application.pricing.CachePriceService;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验价即刷回写（F-6 即时半边）的判据钉死。
 *
 * <p>三条边界与查价口径同源（复用 classifyInventory），各自的错误方向不同：
 * <ul>
 *   <li>在售 → 必须回写，且占用键必须是<b>验价的</b>占用——写错键即静默错键，
 *       长尾占用按需成盘的价值全无；</li>
 *   <li>业务错误/全被过滤（INDETERMINATE）→ 必须不动缓存（F-5.1），
 *       否则一次网络抖动就清掉在售价；</li>
 *   <li>确定无货 → 必须以空列表落缓存，否则僵尸价（B7）借回写还魂。</li>
 * </ul>
 */
class ElongCheckWriteBackTest {

    private ElongPriceServiceImpl service;
    private CachePriceService cachePriceService;

    @BeforeEach
    void setUp() {
        service = new ElongPriceServiceImpl();
        cachePriceService = Mockito.mock(CachePriceService.class);
        ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
        ElongProperties properties = new ElongProperties();
        // productKey 的 account 成分取自 ELONG_USER——键随账号隔离,缺失即拒derive
        ReflectionTestUtils.setField(properties, "user", "test-account");
        ReflectionTestUtils.setField(deriver, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(service, "cachePriceService", cachePriceService);
    }

    private static CheckPriceReq checkReq() {
        return CheckPriceReq.builder()
                .supplierId(10010)
                .sHotelId("61835012").sProductId("whatever")
                .checkIn("2026-08-27").checkOut("2026-08-28")
                .roomNum(1).adultCount(2).childNum(1).childAges(List.of(9))
                .build();
    }

    private static ElongHotelDetailResponse response(String code, ElongHotelDetailResponse.ElongHotel hotel) {
        ElongHotelDetailResponse resp = new ElongHotelDetailResponse();
        ReflectionTestUtils.setField(resp, "code", code);
        if (hotel != null) {
            ElongHotelDetailResponse.Result result = new ElongHotelDetailResponse.Result();
            result.setHotels(List.of(hotel));
            ReflectionTestUtils.setField(resp, "result", result);
        }
        return resp;
    }

    private static ElongHotelDetailResponse.ElongHotel hotelWith(ElongRatePlan... plans) {
        ElongHotelDetailResponse.ElongRoom room = new ElongHotelDetailResponse.ElongRoom();
        room.setRoomId("R1");
        room.setName("豪华双床房");
        room.setRatePlans(List.of(plans));
        ElongHotelDetailResponse.ElongHotel hotel = new ElongHotelDetailResponse.ElongHotel();
        hotel.setHotelId("61835012");
        hotel.setRooms(List.of(room));
        return hotel;
    }

    /** 过三道过滤的最小在售产品（在售+凭据齐+每日价全）。 */
    private static ElongRatePlan sellablePlan() {
        ElongRatePlan plan = new ElongRatePlan();
        plan.setStatus(Boolean.TRUE);
        plan.setRoomTypeId("RT1");
        plan.setGoodsUniqId("goods-1");
        plan.setLittleMajiaId("majia-1");
        plan.setCurrencyCode("RMB");
        ElongNightlyRate nightly = new ElongNightlyRate();
        nightly.setDate("2026-08-27 00:00:00");
        nightly.setRate(new BigDecimal("194.66"));
        nightly.setMinRate(new BigDecimal("181.69"));
        plan.setNightlyRates(List.of(nightly));
        return plan;
    }

    @Test
    @DisplayName("在售 → 回写，且占用键=验价的占用（2大1小9岁 → 2-9）")
    void sellableInventoryIsWrittenBackUnderTheCheckOccupancy() {
        service.doWriteBackFreshInventory(checkReq(), response("0", hotelWith(sellablePlan())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductRespDTO>> products = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PriceReq> priceReq = ArgumentCaptor.forClass(PriceReq.class);
        ArgumentCaptor<Supplier> supplier = ArgumentCaptor.forClass(Supplier.class);
        verify(cachePriceService).productToCache(products.capture(), priceReq.capture(), supplier.capture());

        assertEquals(1, products.getValue().size());
        assertEquals("2-9", priceReq.getValue().getOccupancies().get(0),
                "占用键必须随验价走——写成 1 人档即静默错键");
        assertEquals("2026-08-28", priceReq.getValue().getCheckout(), "CheckPriceReq.checkOut → PriceReq.checkout");
        assertEquals("61835012", supplier.getValue().getSHotelId());
    }

    @Test
    @DisplayName("业务错误 → 不动缓存（F-5.1：没问出结果不清在售价）")
    void businessErrorMustNotTouchCache() {
        service.doWriteBackFreshInventory(checkReq(), response("E1|throttled", null));
        verify(cachePriceService, never()).productToCache(any(), any(), any());
    }

    @Test
    @DisplayName("全被过滤（缺凭据）→ INDETERMINATE → 不动缓存")
    void allFilteredMustNotTouchCache() {
        ElongRatePlan noCreds = sellablePlan();
        noCreds.setGoodsUniqId(null);
        service.doWriteBackFreshInventory(checkReq(), response("0", hotelWith(noCreds)));
        verify(cachePriceService, never()).productToCache(any(), any(), any());
    }

    @Test
    @DisplayName("确定无货 → 空列表落缓存（不落则僵尸价借回写还魂）")
    void confirmedNoInventoryWritesEmptyList() {
        service.doWriteBackFreshInventory(checkReq(), response("0", null));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductRespDTO>> products = ArgumentCaptor.forClass(List.class);
        verify(cachePriceService).productToCache(products.capture(), any(PriceReq.class), any(Supplier.class));
        assertEquals(0, products.getValue().size());
    }

    @Test
    @DisplayName("回写内部炸了只落日志，绝不外抛（验价主流程不受影响）")
    void writeBackFailureIsSwallowed() {
        Mockito.doThrow(new RuntimeException("redis down"))
                .when(cachePriceService).productToCache(any(), any(), any());
        service.doWriteBackFreshInventory(checkReq(), response("0", hotelWith(sellablePlan())));
        // 不抛即过
    }
}
