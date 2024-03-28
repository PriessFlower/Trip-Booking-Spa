package com.bingo.hotel.spa.intl.core.api.aichotels.service;

import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;

public interface AichotelsHotelService {
    void getHotelCodeListByCity(String city);

    AvailabilityResponse getHotelPrice(PriceReq priceReq, String sHotelId);
}
