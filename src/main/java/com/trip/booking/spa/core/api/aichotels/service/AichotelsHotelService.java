package com.trip.booking.spa.core.api.aichotels.service;

import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.core.api.travelconnect.bean.search.response.SearchResponse;

public interface AichotelsHotelService {
    void getHotelCodeListByCity(String city);

    AvailabilityResponse getHotelPrice(PriceReq priceReq, String sHotelId);

    PreBookResponse checkPrice(CheckPriceReq priceReq);
}
