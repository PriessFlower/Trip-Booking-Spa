package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;

/**
 * HotelBookingConfirm（下单）通道。写操作重试恒 0（基类即 0）：重试烧配额且可能双单。
 * 限流键 {@code GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_CREATE_ORDER}。
 */
public class BookingConfirmAccess extends AbstractDidaJsonAccess<DidaBookingConfirmRequest, DidaBookingConfirmResponse> {

    private static final String PATH = "/api/booking/HotelBookingConfirm?$format=json";

    public BookingConfirmAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.CREATE_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CREATE_ORDER, properties, PATH, ORDER_WRITE_SOCKET_TIMEOUT_MS);
    }

    @Override
    protected String errorCode(DidaBookingConfirmResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaBookingConfirmResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaBookingConfirmResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
