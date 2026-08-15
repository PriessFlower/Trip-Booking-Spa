package com.trip.booking.spa.legacy.huitravel.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.legacy.huitravel.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.legacy.huitravel.bean.price.check.CheckResponse;

public interface HuiTravelService {
    void getHotelCodeListByCity(String countryCode,String cityCode);

    AvailabilityResponse getPrice(PriceReq priceReq, String sHotelId);

    AvailabilityResponse getPriceByProductId(CheckPriceReq priceReq);

    CheckResponse checkPrice(CheckPriceReq priceReq);
}
