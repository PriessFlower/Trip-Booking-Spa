package com.trip.booking.spa.legacy.travelconnect.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;

public interface TravelconnectHotelService {
    void getHotelCodeListByCity(String city,String checkIn,String checkOut);

    SearchResponse getHotelPrice(PriceReq priceReq,String sHotelId);

    SearchResponse checkPrice(CheckPriceReq priceReq);
}
