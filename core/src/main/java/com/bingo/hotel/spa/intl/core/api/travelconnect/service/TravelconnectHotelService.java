package com.bingo.hotel.spa.intl.core.api.travelconnect.service;

import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;

public interface TravelconnectHotelService {
    void getHotelCodeListByCity(String city,String checkIn,String checkOut);

    SearchResponse getHotelPrice(PriceReq priceReq,String sHotelId);

    SearchResponse checkPrice(CheckPriceReq priceReq);
}
