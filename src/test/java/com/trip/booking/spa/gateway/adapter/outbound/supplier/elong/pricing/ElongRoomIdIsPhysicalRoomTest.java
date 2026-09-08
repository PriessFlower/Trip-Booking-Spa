package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 契约钉子：艺龙报价里 {@code room.roomId} 必须是<b>物理房型 RoomId</b>（静态接口 / hotel.detail 外层 Room 的号），
 * 不是 RatePlan 的销售房型 RoomTypeId。
 *
 * <p>艺龙有两套号且大多不相等（2026-09-08 生产实测 61504129：报价侧 18 个 RoomTypeId 只有 0029 在静态的 20 个 RoomId 里；
 * 61513057 零重合）。下游 cursor 的 {@code room_physical_mapping} 与 agg 的 {@code room_supplier_ref} 都按静态号建，
 * 此前这里填 RoomTypeId，cursor 归组查不到、整条艺龙报价被丢。
 *
 * <p>销售号仍留在它该待的两处：产品身份（{@code identity.supplierRoomId} / productKey）与下单凭据
 * （{@code ElongOfferCredentials.ROOM_TYPE_ID}）。本测试同时钉住这一点：改 roomId 不许把身份也改了。
 */
class ElongRoomIdIsPhysicalRoomTest {

    private ElongPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ElongPriceServiceImpl();
        ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
        ElongProperties properties = new ElongProperties();
        ReflectionTestUtils.setField(properties, "user", "test-account");
        ReflectionTestUtils.setField(deriver, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);
    }

    private static ElongHotelDetailResponse response(ElongHotelDetailResponse.ElongHotel hotel) {
        ElongHotelDetailResponse resp = new ElongHotelDetailResponse();
        ReflectionTestUtils.setField(resp, "code", "0");
        ElongHotelDetailResponse.Result result = new ElongHotelDetailResponse.Result();
        result.setHotels(List.of(hotel));
        ReflectionTestUtils.setField(resp, "result", result);
        return resp;
    }

    private static ElongRatePlan plan(String roomTypeId, String goods) {
        ElongRatePlan plan = new ElongRatePlan();
        plan.setStatus(Boolean.TRUE);
        plan.setRoomTypeId(roomTypeId);
        plan.setGoodsUniqId(goods);
        plan.setLittleMajiaId("majia-1");
        plan.setCurrencyCode("RMB");
        ElongNightlyRate nightly = new ElongNightlyRate();
        nightly.setDate("2026-09-10 00:00:00");
        nightly.setRate(new BigDecimal("500.00"));
        nightly.setMinRate(new BigDecimal("480.00"));
        plan.setNightlyRates(List.of(nightly));
        return plan;
    }

    private static PriceReq priceReq() {
        PriceReq r = PriceReq.builder()
                .checkIn("2026-09-10").checkout("2026-09-11")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();
        r.setOccupancies(com.trip.booking.spa.gateway.domain.product.Occupancy.perRoom(1, 1, 0, List.of()));
        return r;
    }

    @Test
    @DisplayName("同一物理房 0029 下两个销售房型 0053/0054：报价 roomId 都是 0029，身份里的 supplierRoomId 仍是各自的销售号")
    void roomIdIsParentPhysicalRoomIdentityKeepsRoomTypeId() {
        ElongHotelDetailResponse.ElongRoom room = new ElongHotelDetailResponse.ElongRoom();
        room.setRoomId("0029");
        room.setName("高级大床房");
        room.setRatePlans(List.of(plan("0053", "g-53"), plan("0054", "g-54")));
        ElongHotelDetailResponse.ElongHotel hotel = new ElongHotelDetailResponse.ElongHotel();
        hotel.setHotelId("61504129");
        hotel.setRooms(List.of(room));

        List<ProductRespDTO> products = service.freshProducts(response(hotel), priceReq(), "61504129");
        assertEquals(2, products.size());
        for (ProductRespDTO p : products) {
            assertEquals("0029", p.getRoom().getRoomId(), "报价的房型号必须是物理 RoomId，下游按它对静态数据");
            assertEquals("高级大床房", p.getRoom().getRoomName());
        }
        assertEquals(List.of("0053", "0054"),
                products.stream().map(p -> p.getIdentity().supplierRoomId()).sorted().toList(),
                "产品身份仍按销售房型 RoomTypeId 算，不许跟着 roomId 一起改");
        assertEquals(2, products.stream().map(ProductRespDTO::getProductKey).distinct().count(),
                "两个销售房型是两个产品，productKey 不能撞");
    }
}
