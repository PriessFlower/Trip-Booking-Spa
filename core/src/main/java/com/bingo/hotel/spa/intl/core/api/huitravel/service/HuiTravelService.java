package com.bingo.hotel.spa.intl.core.api.huitravel.service;

import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckResponse;

public interface HuiTravelService {
    void getHotelCodeListByCity(String countryCode,String cityCode);

    AvailabilityResponse getPrice(PriceReq priceReq, String sHotelId);

    AvailabilityResponse getPriceByProductId(CheckPriceReq priceReq);

    CheckResponse checkPrice(CheckPriceReq priceReq);
}
