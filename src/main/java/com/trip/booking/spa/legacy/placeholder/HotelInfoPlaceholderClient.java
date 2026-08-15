package com.trip.booking.spa.legacy.placeholder;

import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.QueryHotelRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierHotelBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierProductBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierRoomBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.response.PageResp;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.response.SupplierHotelBaseResponse;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.result.InfoResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compile-time placeholder for the removed hotel-info-intl service.
 * Every operation fails explicitly until the local evidence layer replaces it.
 */
@Component
public class HotelInfoPlaceholderClient {

    public InfoResult saveHotelInfo(List<SupplierHotelBaseRequest> requests) {
        return unsupported("saveHotelInfo");
    }

    public InfoResult saveRoomInfo(List<SupplierRoomBaseRequest> requests) {
        return unsupported("saveRoomInfo");
    }

    public InfoResult saveProductInfo(List<SupplierProductBaseRequest> requests) {
        return unsupported("saveProductInfo");
    }

    public InfoResult<List<SupplierHotelBaseResponse>> queryHotelList(QueryHotelRequest request) {
        return unsupported("queryHotelList");
    }

    public InfoResult<PageResp<SupplierHotelBaseResponse>> queryHotelPageList(QueryHotelRequest request) {
        return unsupported("queryHotelPageList");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException(
                "Supplier evidence operation '" + operation + "' is unavailable: hotel-info-intl was removed");
    }
}
