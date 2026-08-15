package com.trip.booking.spa.legacy.huitravel;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.legacy.huitravel.bean.price.availability.NightlyRate;
import com.trip.booking.spa.legacy.huitravel.bean.price.check.CheckResponse;
import com.trip.booking.spa.legacy.huitravel.service.HuiTravelService;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
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
        redisRecordLogServiceImpl.recordHuiTravelQps();
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
                .outcome(CheckPriceOutcome.BOOKABLE)
                .salePrice(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .totalPriceAfter(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .totalPriceBefore(checkResponse.getResult().getNightlyrate().stream()
                        .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                .message("CNY")
                .bedTypeCode(list)
                .build();
    }
}
