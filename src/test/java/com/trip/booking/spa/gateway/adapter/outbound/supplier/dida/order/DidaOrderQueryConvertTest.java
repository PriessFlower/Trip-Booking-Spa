package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOrderWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaError;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住查单响应 → 三态与字段。<b>NOT_FOUND 永不判</b>：官方明示空返回不能视为终态，且生产实证空列表
 * 出现在建单窗口内（2026-09-12 22:15:07 同一 ClientReference 两次皆空）。
 */
class DidaOrderQueryConvertTest {

    private final DidaOrderQuerySyncServiceImpl service = new DidaOrderQuerySyncServiceImpl();

    @Test
    @DisplayName("真实 Status=2 且带酒店确认号 → FOUND / 21 / HCN / 总价分+币种 / 下单时间")
    void confirmedWithHcn() throws IOException {
        OrderRespDTO dto = service.orderQueryRespConvert(
                DidaOrderWireNameTest.read("/dida/booking-search-hcn-real-20260914.json", DidaBookingSearchResponse.class));
        assertEquals(OrderPresence.FOUND, dto.getPresence());
        assertEquals("18577514927", dto.getSupplierOrderId());
        assertEquals(21, dto.getOrderStatus());
        assertEquals("2", dto.getSupplierOrderStatus());
        assertEquals("78112572", dto.getConfirmationNumber());
        assertEquals(210400, dto.getTotalPrice());
        assertEquals("CNY", dto.getTotalPriceCurrency());
        assertEquals("2026-09-01 20:12:27.720", dto.getCreateTime());
    }

    @Test
    @DisplayName("Status 3/4/5 → 31/22/20；HCN 缺席为 null 不是空串")
    void statusMapping() throws IOException {
        assertEquals(31, service.orderQueryRespConvert(DidaOrderWireNameTest.read(
                "/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class)).getOrderStatus());
        assertEquals(22, service.orderQueryRespConvert(DidaOrderWireNameTest.read(
                "/dida/booking-search-status4-real-20260910.json", DidaBookingSearchResponse.class)).getOrderStatus());
        OrderRespDTO pending = service.orderQueryRespConvert(DidaOrderWireNameTest.read(
                "/dida/booking-search-status5-real-20260913.json", DidaBookingSearchResponse.class));
        assertEquals(20, pending.getOrderStatus());
        assertEquals(OrderPresence.FOUND, pending.getPresence());
        assertNull(pending.getConfirmationNumber());
    }

    @Test
    @DisplayName("空列表 → INDETERMINATE，绝不 NOT_FOUND")
    void emptyListIsIndeterminate() throws IOException {
        OrderRespDTO dto = service.orderQueryRespConvert(
                DidaOrderWireNameTest.read("/dida/booking-search-empty-real-20260912.json", DidaBookingSearchResponse.class));
        assertEquals(OrderPresence.INDETERMINATE, dto.getPresence());
        assertNull(dto.getSupplierOrderId());
    }

    @Test
    @DisplayName("错误信封（含 3003 BookingID 不正确）→ INDETERMINATE：「不正确」分不清是没有还是写错")
    void errorIsIndeterminate() {
        DidaError error = new DidaError();
        error.setCode("3003");
        error.setMessage("Invalid BookingID");
        DidaBookingSearchResponse resp = new DidaBookingSearchResponse();
        resp.setError(error);
        OrderRespDTO dto = service.orderQueryRespConvert(resp);
        assertEquals(OrderPresence.INDETERMINATE, dto.getPresence());
    }
}
