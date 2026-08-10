package com.trip.booking.spa.core.api.expedia.service.impl;

import com.trip.booking.spa.core.api.common.enums.OrderPresence;
import com.trip.booking.spa.core.api.dto.OrderRespDTO;
import com.trip.booking.spa.core.api.expedia.bean.response.CreateOrderResponse;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryOrderResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉死 Expedia 查单的两处判定：三态映射，以及「状态映射不上时留空而不猜」。
 */
class ExpediaOrderQuerySyncServiceImplTest {

    private final ExpediaOrderQuerySyncServiceImpl service = new ExpediaOrderQuerySyncServiceImpl();

    private static CreateOrderResponse.Room room(String status) {
        CreateOrderResponse.Room r = new CreateOrderResponse.Room();
        r.setStatus(status);
        return r;
    }

    // ── 三态映射 ────────────────────────────────────────────

    /** Expedia 空数组是「确实没有这笔订单」，这是唯一允许上游重新下单的一态 */
    @Test
    void emptyArrayMapsToNotFound() {
        OrderRespDTO resp = service.orderQueryRespConvert(QueryOrderResponse.of("[]"));

        assertEquals(OrderPresence.NOT_FOUND, resp.getPresence());
    }

    /** 错误响应必须落 INDETERMINATE，不可当成「订单不存在」 */
    @Test
    void errorObjectMapsToIndeterminate() {
        QueryOrderResponse raw = QueryOrderResponse.of(
                "{\"type\":\"internal_server_error\",\"message\":\"boom\"}");

        OrderRespDTO resp = service.orderQueryRespConvert(raw);

        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence(),
                "查单失败若被当成订单不存在，上游会重复下单");
        assertEquals("boom", resp.getMessage());
    }

    /** 查到订单时取出订单号、确认号与状态 */
    @Test
    void foundItineraryIsMapped() {
        String body = "[{\"itinerary_id\":\"7933703956082\","
                + "\"affiliate_reference_id\":\"UPSTREAM-1\","
                + "\"rooms\":[{\"status\":\"booked\","
                + "\"confirmation_id\":{\"expedia\":\"705701798359385\"}}]}]";

        OrderRespDTO resp = service.orderQueryRespConvert(QueryOrderResponse.of(body));

        assertEquals(OrderPresence.FOUND, resp.getPresence());
        assertEquals("7933703956082", resp.getSupplierOrderId());
        assertEquals("705701798359385", resp.getConfirmationNumber());
        assertEquals(21, resp.getOrderStatus());
        assertEquals("booked", resp.getSupplierOrderStatus());
    }

    /** 声称查到却没有订单号，属响应自相矛盾，不可按查到处理 */
    @Test
    void foundWithoutItineraryIdIsIndeterminate() {
        OrderRespDTO resp = service.orderQueryRespConvert(
                QueryOrderResponse.of("[{\"affiliate_reference_id\":\"UPSTREAM-1\"}]"));

        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence());
    }

    // ── 状态映射：不认识就留空 ────────────────────────────────

    @Test
    void allCanceledMapsToCancelSuccess() {
        assertEquals(31, ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(
                List.of(room("canceled"), room("canceled"))));
    }

    /** 部分取消不等于整单取消：只要还有已订的房间，订单仍是预定成功 */
    @Test
    void partiallyCanceledStaysBookSuccess() {
        assertEquals(21, ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(
                List.of(room("booked"), room("canceled"))));
    }

    @Test
    void pendingMapsToBooking() {
        assertEquals(20, ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(List.of(room("pending"))));
    }

    /**
     * 供应商新增或改写状态取值时必须留空，由 supplierOrderStatus 保留原文。
     * 猜默认值会把未知状态说成已知——上游据此做的每一步都是错的。
     */
    @Test
    void unrecognizedStatusYieldsNullNotAGuess() {
        assertNull(ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(List.of(room("on_request"))),
                "未知状态不得被映射成任何已知状态");
        assertNull(ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(List.of(room(null))));
    }

    /** 未知状态与已取消混在一起时，不能因「其余都取消了」就断言整单取消 */
    @Test
    void unknownMixedWithCanceledIsNotReportedAsCanceled() {
        assertNull(ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(
                List.of(room("canceled"), room("on_request"))),
                "存在读不懂的房间状态时，整单状态同样读不懂");
    }

    @Test
    void noRoomsYieldsNull() {
        assertNull(ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(null));
        assertNull(ExpediaOrderQuerySyncServiceImpl.mapOrderStatus(List.of()));
    }
}
