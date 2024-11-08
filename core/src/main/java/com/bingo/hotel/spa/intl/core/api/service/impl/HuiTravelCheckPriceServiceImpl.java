package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.NightlyRate;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.service.HuiTravelService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractCheckPriceSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;

@Service("huitravelCheckPriceSyncService")
public class HuiTravelCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckResponse> {

    @Autowired
    HuiTravelService huiTravelService;

    @Resource(name = "redisRecordLogServiceImpl")
    RecordLogService redisRecordLogServiceImpl;

    @Override
    public CheckResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        redisRecordLogServiceImpl.recordDaolvQps();
        return huiTravelService.checkPrice(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckResponse checkResponse) {
        ArrayList<String> list = new ArrayList<>();
        list.add(String.valueOf(checkResponse.getResult().getHid()));
        list.add(String.valueOf(checkResponse.getResult().getRid()));
        list.add(String.valueOf(checkResponse.getResult().getRpid()));
        StringBuilder costs = new StringBuilder();
        for (NightlyRate item : checkResponse.getResult().getNightlyrate()) {
            costs.append(",").append(item.getCost());
        }
        return CheckPriceRespDTO.builder()
                .checkStatus(true)
                .salePrice(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .totalPriceAfter(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .totalPriceBefore(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .prebookToken(costs.substring(1))
                .message("CNY")
                .bedTypeCode(list)
                .build();
    }
}
