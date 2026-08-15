package com.trip.booking.spa.legacy.meituan;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.legacy.meituan.service.MeituanPriceService;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("meituanCheckPriceSyncService")
public class MeiTuanCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Autowired
    MeituanPriceService meituanPriceService;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return meituanPriceService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO checkResponse) {
        return checkResponse;
    }
}
