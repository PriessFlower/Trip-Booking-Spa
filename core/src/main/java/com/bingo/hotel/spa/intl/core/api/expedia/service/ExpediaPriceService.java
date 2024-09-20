package com.bingo.hotel.spa.intl.core.api.expedia.service;

import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckResponse;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaPriceService {

    AvailabilityResponse queryPrice(PriceReq request);

    CheckResponse checkPrice(CheckPriceReq request);

}
