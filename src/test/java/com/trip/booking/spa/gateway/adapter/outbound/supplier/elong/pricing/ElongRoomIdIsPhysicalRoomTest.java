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
 * <p>2026-09-08 第二步：身份的房型成分也改为物理号（此前用销售号的依据「cursor 以其为等价锚」核实为误），
 * 与其他五家同口径；销售号只留在下单凭据（{@code ElongOfferCredentials.ROOM_TYPE_ID}）。
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
    @DisplayName("同一物理房 0029 下两个销售房型 0053/0054：报价 roomId 与身份房型成分都是 0029，两条落同一等价类")
    void roomIdAndIdentityAreParentPhysicalRoom() {
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
        for (ProductRespDTO p : products) {
            assertEquals("0029", p.getIdentity().supplierRoomId(),
                    "身份的房型成分也是物理号（2026-09-08 起），与其他五家同口径；销售号只在下单凭据里");
        }
        assertEquals(1, products.stream().map(ProductRespDTO::getProductKey).distinct().count(),
                "同物理房、同餐食退改占用的两个销售房型 = 同一等价类，换票时按容差取最低价");
        assertEquals(List.of("g-53", "g-54"),
                products.stream().map(ProductRespDTO::getProductId).sorted().toList(),
                "报价码仍各自保留，下单凭据走它们");
    }
}
