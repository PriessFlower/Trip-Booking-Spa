package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaPriceService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractCheckPriceSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;

@Service("expediaCheckPriceService")
public class ExpediaCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceResponse> {

    @Autowired
    ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    RecordLogService redisRecordLogServiceImpl;

    @Override
    public CheckPriceResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        return expediaPriceService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceResponse checkResponse) {
        return CheckPriceRespDTO.builder()
                .checkStatus(true)
                .prebookToken(checkResponse.getLinks().getBook().getHref())
                .salePrice(new BigDecimal(checkResponse.getOccupancy_pricing().getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .subPrice(new BigDecimal(checkResponse.getOccupancy_pricing().getTotals().getMarketing_fee().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .build();
    }
}
