package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ResponseDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.util.SpringAppContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SpaControllerUnsupportedOperationTest {

    private static final int EXPEDIA = SupplierSourceEnum.EXPEDIA.getCode();

    private final SpaController controller = new SpaController();

    @BeforeEach
    void setUpApplicationContext() {
        SpringAppContextUtil.AppContext.setApplicationContextHolder(mock(ApplicationContext.class));
    }

    @AfterEach
    void clearApplicationContext() {
        SpringAppContextUtil.AppContext.setApplicationContextHolder(null);
    }

    @Test
    void returnsExplicitErrorWhenBookingIsNotImplemented() {
        BookingReq request = BookingReq.builder()
                .supplierId(EXPEDIA)
                .orderId("order-1")
                .personName("guest")
                .contactName("contact")
                .contactPhone("10086")
                .checkIn("2026-08-10")
                .checkOut("2026-08-11")
                .roomNum(1)
                .totalPrice(10000)
                .settlePrice(9000)
                .build();

        assertUnsupported(controller.booking(request), "booking");
    }

    @Test
    void returnsExplicitErrorWhenCancellationIsNotImplemented() {
        CancelReq request = CancelReq.builder()
                .supplierId(EXPEDIA)
                .supplierOrderId("supplier-order-1")
                .orderId("order-1")
                .build();

        assertUnsupported(controller.cancel(request), "cancel");
    }

    @Test
    void returnsExplicitErrorWhenOrderQueryIsNotImplemented() {
        OrderQueryReq request = OrderQueryReq.builder()
                .supplierId(EXPEDIA)
                .supplierOrderId("supplier-order-1")
                .orderId("order-1")
                .build();

        assertUnsupported(controller.orderQuery(request), "order");
    }

    private void assertUnsupported(ResponseDTO<?> response, String operation) {
        assertFalse(response.getSuccess());
        assertEquals(ResponseDTO.STATUS_FAIL, response.getCode());
        assertEquals("supplier operation is not available: supplierId="
                + EXPEDIA + ", operation=" + operation, response.getErrorMsg());
    }
}
