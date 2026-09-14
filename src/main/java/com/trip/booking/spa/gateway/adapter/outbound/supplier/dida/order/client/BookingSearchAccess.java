package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRequestHeader;

/**
 * HotelBookingSearch（查单）通道。下单的疑似重复反查、取消前取道旅单号、取消后确证状态
 * 都走它，故请求装配收在通道上共用。限流键 {@code GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_QUERY_ORDER}。
 */
public class BookingSearchAccess extends AbstractDidaJsonAccess<DidaBookingSearchRequest, DidaBookingSearchResponse> {

    private static final String PATH = "/api/booking/HotelBookingSearch?$format=json";

    public BookingSearchAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.QUERY_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_QUERY_ORDER, properties, PATH, ORDER_READ_SOCKET_TIMEOUT_MS);
    }

    /** 按道旅单号查（官方「使用道旅订单号搜索 推荐」） */
    public static DidaBookingSearchRequest byBookingId(DidaProperties properties, String bookingId) {
        DidaBookingSearchRequest.SearchBy searchBy = new DidaBookingSearchRequest.SearchBy();
        searchBy.setBookingId(bookingId);
        return request(properties, searchBy);
    }

    /** 按我方单号查（官方「使用客户订单号搜索 推荐」；下单时 ClientReference=我方单号） */
    public static DidaBookingSearchRequest byClientReference(DidaProperties properties, String clientReference) {
        DidaBookingSearchRequest.BookingInfo info = new DidaBookingSearchRequest.BookingInfo();
        info.setClientReference(clientReference);
        DidaBookingSearchRequest.SearchBy searchBy = new DidaBookingSearchRequest.SearchBy();
        searchBy.setBookingInfo(info);
        return request(properties, searchBy);
    }

    private static DidaBookingSearchRequest request(DidaProperties properties, DidaBookingSearchRequest.SearchBy searchBy) {
        DidaBookingSearchRequest request = new DidaBookingSearchRequest();
        request.setHeader(new DidaRequestHeader(properties.getClientId(), properties.getLicenseKey()));
        request.setSearchBy(searchBy);
        return request;
    }

    @Override
    protected String errorCode(DidaBookingSearchResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaBookingSearchResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaBookingSearchResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
