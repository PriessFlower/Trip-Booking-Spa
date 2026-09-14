package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;

/**
 * HotelBookingCancel（预取消）通道：取罚金与 10 分钟有效的取消确认号，本身不取消订单。
 * 与确认取消共用限流键 {@code GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_CANCEL_ORDER}——一次取消扣两格。
 */
public class BookingCancelAccess extends AbstractDidaJsonAccess<DidaBookingCancelRequest, DidaBookingCancelResponse> {

    private static final String PATH = "/api/booking/HotelBookingCancel?$format=json";

    public BookingCancelAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.CANCEL_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CANCEL_ORDER, properties, PATH, ORDER_READ_SOCKET_TIMEOUT_MS);
    }

    @Override
    protected String errorCode(DidaBookingCancelResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaBookingCancelResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaBookingCancelResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
