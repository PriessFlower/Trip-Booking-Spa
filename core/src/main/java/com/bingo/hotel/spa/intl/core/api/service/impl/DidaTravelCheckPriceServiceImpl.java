package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractCheckPriceSyncSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service("didatravelCheckPriceSyncService")
public class DidaTravelCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<PriceConfirmResponse> {

    @Autowired
    DidatravelHotelService didatravelHotelService;

    @Override
    public PriceConfirmResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        return didatravelHotelService.checkPrice(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(PriceConfirmResponse priceConfirmResponse) {

        PriceConfirmResponse.HotelTypeRatePlan plan = priceConfirmResponse.getSuccess().getPriceDetails().getHotelList().get(0).getRatePlanList().get(0);
        ArrayList<String> list = new ArrayList<>();
        list.add(String.valueOf(plan.getBedType()));

        return CheckPriceRespDTO.builder()
//                .referenceNo(priceConfirmResponse.getSuccess().getPriceDetails().getReferenceNo())
                .checkStatus(true)
                .prebookToken(priceConfirmResponse.getSuccess().getPriceDetails().getReferenceNo())
                .salePrice(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .totalPriceAfter(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .totalPriceBefore(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .bedTypeCode(list)
                .build();
    }
}
