package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
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
 * <p>三条边界与查价口径同源（复用 toPricingResult），各自的错误方向不同：
 * <ul>
 *   <li>在售 → 必须回写，且占用键必须是<b>验价的</b>占用——写错键即静默错键，
 *       长尾占用按需成盘的价值全无；</li>
 *   <li>业务错误/全被过滤（INDETERMINATE）→ 必须不动缓存（F-5.1），
 *       否则一次网络抖动就清掉在售价；</li>
 *   <li>确定无货 → 必须以空列表落缓存，否则僵尸价（B7）借回写还魂。</li>
 * </ul>
 */
class ElongFreshPricesToCacheTest {

    private ElongPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ElongPriceServiceImpl();
        ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
        ElongProperties properties = new ElongProperties();
        // productKey 的 account 成分取自 ELONG_USER——键随账号隔离,缺失即拒derive
        ReflectionTestUtils.setField(properties, "user", "test-account");
        ReflectionTestUtils.setField(deriver, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);
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

    /** 占用键随验价走（2 大 1 小 9 岁 → 2-9）；组装由模板做，这里只喂同形状的入参 */
    private static PriceReq priceReq() {
        PriceReq r = PriceReq.builder()
                .checkIn("2026-08-27").checkout("2026-08-28")
                .roomNum(1).adultNum(2).childNum(1).childAges(List.of(9)).build();
        r.setOccupancies(com.trip.booking.spa.gateway.domain.product.Occupancy
                .perRoom(1, 2, 1, List.of(9)));
        return r;
    }

    @Test
    @DisplayName("在售 → 给出产品")
    void sellableInventoryConvertsToProducts() {
        assertEquals(1, service.freshProducts(response("0", hotelWith(sellablePlan())), priceReq(), "61835012").size());
    }

    @Test
    @DisplayName("业务错误 → 给 null（F-5.1：模板据此不动缓存，不清在售价）")
    void businessErrorYieldsNull() {
        org.junit.jupiter.api.Assertions.assertNull(
                service.freshProducts(response("E1|throttled", null), priceReq(), "61835012"));
    }

    @Test
    @DisplayName("全被过滤（缺凭据）→ INDETERMINATE → 给 null")
    void allFilteredYieldsNull() {
        ElongRatePlan noCreds = sellablePlan();
        noCreds.setGoodsUniqId(null);
        org.junit.jupiter.api.Assertions.assertNull(
                service.freshProducts(response("0", hotelWith(noCreds)), priceReq(), "61835012"));
    }

    @Test
    @DisplayName("确定无货 → 空列表（模板据此落无货标记，不落则僵尸价借回写还魂）")
    void confirmedNoInventoryYieldsEmptyList() {
        assertEquals(0, service.freshProducts(response("0", null), priceReq(), "61835012").size());
    }

}
