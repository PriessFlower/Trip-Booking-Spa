package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.cli.dto.CheckPriceRespDTO;
import com.trip.booking.spa.cli.seq.CheckPriceReq;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkService;
import com.trip.booking.spa.core.api.service.AbstractCheckPriceSyncSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fastpay验价.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/28
 * @since : 1.0
 **/

@Service("ratehawkCheckPriceSyncService")
public class RatehawkCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Autowired
    private RateHawkService rateHawkService;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return rateHawkService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO checkResponse) {
        return checkResponse;
    }
}
