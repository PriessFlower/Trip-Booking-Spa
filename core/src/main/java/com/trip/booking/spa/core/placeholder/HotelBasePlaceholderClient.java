package com.trip.booking.spa.core.placeholder;

import com.trip.booking.spa.core.placeholder.hotelbase.request.CityInfoRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.CountryInfoRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.GlobalProductSupplierRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.HotelDetailsRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.QueryHotelRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.QueryRoomRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.request.SupplierHotelInfoRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.response.GetCityInfoBySupplierHotelIdResponse;
import com.trip.booking.spa.core.placeholder.hotelbase.response.HotelBaseResponse;
import com.trip.booking.spa.core.placeholder.hotelbase.response.RoomBaseResponse;
import com.trip.booking.spa.core.placeholder.hotelbase.result.BaseResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compile-time placeholder for the removed hotel-base-intl service.
 * Every operation fails explicitly until an internal catalog implementation replaces it.
 */
@Component
public class HotelBasePlaceholderClient {

    public BaseResult saveCityList(List<CityInfoRequest> requests) {
        return unsupported("saveCityList");
    }

    public BaseResult saveCountryList(List<CountryInfoRequest> requests) {
        return unsupported("saveCountryList");
    }

    public BaseResult saveHotelDetails(List<HotelDetailsRequest> requests) {
        return unsupported("saveHotelDetails");
    }

    public BaseResult removeHotelDetails(List<String> hotelIds) {
        return unsupported("removeHotelDetails");
    }

    public BaseResult aggregatorProductMapping(List<GlobalProductSupplierRequest> requests) {
        return unsupported("aggregatorProductMapping");
    }

    public BaseResult<List<GetCityInfoBySupplierHotelIdResponse>> getAllCityInfoBySupplierId(List<String> supplierIds) {
        return unsupported("getAllCityInfoBySupplierId");
    }

    public BaseResult<GetCityInfoBySupplierHotelIdResponse> getCityInfoBySupplierHotelId(
            SupplierHotelInfoRequest request) {
        return unsupported("getCityInfoBySupplierHotelId");
    }

    public BaseResult<List<GetCityInfoBySupplierHotelIdResponse>> getCityInfoByHotelIds(List<String> hotelIds) {
        return unsupported("getCityInfoByHotelIds");
    }

    public BaseResult<List<HotelBaseResponse>> queryHotelBaseList(QueryHotelRequest request) {
        return unsupported("queryHotelBaseList");
    }

    public BaseResult<List<RoomBaseResponse>> queryRoomBaseList(QueryRoomRequest request) {
        return unsupported("queryRoomBaseList");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException(
                "Hotel catalog operation '" + operation + "' is unavailable: hotel-base-intl was removed");
    }
}
