package com.trip.booking.spa.core.api.travelconnect.service;

import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.travelconnect.bean.search.response.SearchResponse;

public interface TravelconnectHotelService {
    void getHotelCodeListByCity(String city,String checkIn,String checkOut);

    SearchResponse getHotelPrice(PriceReq priceReq,String sHotelId);

    SearchResponse checkPrice(CheckPriceReq priceReq);
}
