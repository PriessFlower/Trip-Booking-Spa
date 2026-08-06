package com.trip.booking.spa.core.api.didatravel.service;

import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.didatravel.bean.price.DidaTravelResponse;
import com.trip.booking.spa.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;

/**
 * @author EDY
 */
public interface DidatravelHotelService {
    void queryAndSaveStaticInfo(String staticType, String startTime, String endTime, int startNum, int endNum, boolean downloadFlag);

     DidaTravelResponse getHotelService(PriceReq priceReq, String sHotelId);


    PriceConfirmResponse checkPrice(CheckPriceReq checkPriceReq);
    PriceConfirmResponse checkPrice(CheckPriceReq checkPriceReq, boolean preBook);

}
