package com.trip.booking.spa.legacy.aichotels.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.legacy.aichotels.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;

public interface AichotelsHotelService {
    void getHotelCodeListByCity(String city);

    AvailabilityResponse getHotelPrice(PriceReq priceReq, String sHotelId);

    PreBookResponse checkPrice(CheckPriceReq priceReq);
}
