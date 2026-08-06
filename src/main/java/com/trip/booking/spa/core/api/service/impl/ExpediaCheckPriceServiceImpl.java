package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.expedia.service.ExpediaPriceService;
import com.trip.booking.spa.core.api.service.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service("expediaCheckPriceSyncService")
public class ExpediaCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Autowired
    ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    RecordLogService redisRecordLogServiceImpl;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return expediaPriceService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO checkResponse) {
        return checkResponse;
    }
}
