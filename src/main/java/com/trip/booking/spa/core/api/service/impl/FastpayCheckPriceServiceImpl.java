package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.fastpay.service.FastPayService;
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

@Service("FastpayHotelsCheckPriceSyncService")
public class FastpayCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Autowired
    private FastPayService fastPayService;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return fastPayService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO checkResponse) {
        return checkResponse;
    }
}
