package com.trip.booking.spa.core.api.huitravel.service;

import com.trip.booking.spa.cli.seq.CheckPriceReq;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.core.api.huitravel.bean.price.check.CheckResponse;

public interface HuiTravelService {
    void getHotelCodeListByCity(String countryCode,String cityCode);

    AvailabilityResponse getPrice(PriceReq priceReq, String sHotelId);

    AvailabilityResponse getPriceByProductId(CheckPriceReq priceReq);

    CheckResponse checkPrice(CheckPriceReq priceReq);
}
