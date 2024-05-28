package com.bingo.hotel.spa.intl.core.api.didatravel.service;

import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;

/**
 * @author EDY
 */
public interface DidatravelHotelService {
     void queryAndSaveStaticInfo (String staticType);

     DidaTravelResponse getHotelService(PriceReq priceReq, String sHotelId);

     PriceConfirmResponse checkPrice(CheckPriceReq checkPriceReq);

}
