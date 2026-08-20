package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住：<b>供应商把酒店回来了但一条报价都没给 = 确定无货</b>，不是「没问出来」。
 *
 * <p>2026-08-20 生产实测的回归：{@code isEmptyResult()} 只看 {@code Hotels} 是否为空，
 * 盖不住「酒店在、rateplan 为 0」这一形态。当时把它判成 INDETERMINATE，一轮 900 行里
 * 有 134 个酒店-日期落进来——而 INDETERMINATE 依 F-5.1 <b>不动缓存</b>，于是这些已经
 * 没货的酒店留着上一轮的陈价继续对外报，直到 TTL 过期。那正是 gateway-boundary B7
 * 说的僵尸价（dida 339 个死 id 残留 66,469 行在售僵尸价）。
 *
 * <p>与「供应商给了报价、但被我方三道过滤全丢」必须分开：后者房其实还在，
 * 说成无房会劝退旅客，故仍取安全侧 INDETERMINATE。
 */
class ElongZeroPlanIsNoInventoryTest {

    private static int countPlans(ElongHotelDetailResponse.ElongHotel hotel) {
        return (int) ReflectionTestUtils.invokeMethod(ElongPriceServiceImpl.class, "countPlans", hotel);
    }

    private static ElongHotelDetailResponse.ElongHotel hotel(List<ElongHotelDetailResponse.ElongRoom> rooms) {
        ElongHotelDetailResponse.ElongHotel h = new ElongHotelDetailResponse.ElongHotel();
        h.setHotelId("61501827");
        h.setRooms(rooms);
        return h;
    }

    private static ElongHotelDetailResponse.ElongRoom room(int planCount) {
        ElongHotelDetailResponse.ElongRoom r = new ElongHotelDetailResponse.ElongRoom();
        r.setRoomId("0001");
        r.setRatePlans(planCount == 0 ? List.of()
                : java.util.stream.IntStream.range(0, planCount)
                        .mapToObj(i -> new com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan())
                        .toList());
        return r;
    }

    @Test
    @DisplayName("酒店在、房型为空 → 报价数 0")
    void hotelWithNoRoomsHasNoPlans() {
        assertEquals(0, countPlans(hotel(null)));
        assertEquals(0, countPlans(hotel(List.of())));
    }

    @Test
    @DisplayName("酒店在、房型在、但 rateplan 为空 → 报价数 0（生产上那 134 个就是这一种）")
    void hotelWithRoomsButNoRatePlansHasNoPlans() {
        assertEquals(0, countPlans(hotel(List.of(room(0), room(0)))));
    }

    @Test
    @DisplayName("有报价时如实计数——它与「被过滤全丢」的区分全靠这个数")
    void countsPlansAcrossRooms() {
        assertEquals(5, countPlans(hotel(List.of(room(2), room(3)))));
    }
}
