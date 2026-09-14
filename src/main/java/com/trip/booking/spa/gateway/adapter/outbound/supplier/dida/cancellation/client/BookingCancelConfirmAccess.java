package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;

/**
 * HotelBookingCancelConfirm（确认取消）通道，真正取消订单的那一步。写操作重试恒 0：
 * cursor 生产 2026-09-10 同一单三次确认全部读超时，随后预取消回 3018「已取消」——
 * 超时的那次已经生效，重发只是重复动作。限流键同预取消。
 */
public class BookingCancelConfirmAccess
        extends AbstractDidaJsonAccess<DidaBookingCancelConfirmRequest, DidaBookingCancelConfirmResponse> {

    private static final String PATH = "/api/booking/HotelBookingCancelConfirm?$format=json";

    public BookingCancelConfirmAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.CANCEL_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CANCEL_ORDER, properties, PATH, ORDER_WRITE_SOCKET_TIMEOUT_MS);
    }

    @Override
    protected String errorCode(DidaBookingCancelConfirmResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaBookingCancelConfirmResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaBookingCancelConfirmResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
