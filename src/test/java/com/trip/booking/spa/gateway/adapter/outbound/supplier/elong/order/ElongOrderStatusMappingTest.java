package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order;

import org.junit.jupiter.api.Test;

import com.trip.booking.spa.gateway.domain.booking.OrderState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 状态映射判据钉死（官方状态表）。核心纪律：识别不出返回 null 保留原文，
 * 禁止默认值——cursor 反面：把 H"变更"映成"已取消"。
 */
class ElongOrderStatusMappingTest {

    @Test
    void establishedStatesMapToBookSuccess() {
        for (String s : new String[]{"A", "B", "C", "F"}) {
            assertEquals(OrderState.BOOKED, ElongOrderQuerySyncServiceImpl.mapOrderStatus(s), s);
        }
    }

    @Test
    void inFlightStatesMapToBooking() {
        for (String s : new String[]{"N", "V", "B1", "B2", "B3", "G", "H"}) {
            assertEquals(OrderState.BOOKING, ElongOrderQuerySyncServiceImpl.mapOrderStatus(s), s);
        }
    }

    @Test
    void cancellingInFlightMapsToCanceling() {
        // E1=取消处理中:真单实测(101067194262)取消受理后立即出现,~30 秒后翻转为 D
        assertEquals(OrderState.CANCELING, ElongOrderQuerySyncServiceImpl.mapOrderStatus("E1"));
    }

    @Test
    void cancelledStatesMapToCancelSuccess() {
        for (String s : new String[]{"E", "D", "Z"}) {
            assertEquals(OrderState.CANCELED, ElongOrderQuerySyncServiceImpl.mapOrderStatus(s), s);
        }
    }

    @Test
    void soldOutStatesMapToBookFail() {
        assertEquals(OrderState.BOOK_FAILED, ElongOrderQuerySyncServiceImpl.mapOrderStatus("O"));
        assertEquals(OrderState.BOOK_FAILED, ElongOrderQuerySyncServiceImpl.mapOrderStatus("U"));
    }

    @Test
    void unknownStatesStayNull() {
        assertNull(ElongOrderQuerySyncServiceImpl.mapOrderStatus("S"));
        assertNull(ElongOrderQuerySyncServiceImpl.mapOrderStatus("X9"));
        assertNull(ElongOrderQuerySyncServiceImpl.mapOrderStatus(null));
        assertNull(ElongOrderQuerySyncServiceImpl.mapOrderStatus(" "));
    }
}
