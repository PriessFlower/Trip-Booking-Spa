package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaPriceService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractCheckPriceSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Service("expediaCheckPriceSyncService")
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
        CheckPriceResponse.Occupancy_pricing occupancyPricing = checkResponse.getOccupancy_pricing().get(checkResponse.getAdultCount().toString());
        return CheckPriceRespDTO.builder()
                .checkStatus(true)
                .prebookToken(null == checkResponse.getLinks().getBook() ? checkResponse.getLinks().getCommit().getHref() :
                        checkResponse.getLinks().getBook().getHref())
                .salePrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .subPrice(null == occupancyPricing.getTotals().getMarketing_fee() ? 0 :
                        new BigDecimal(occupancyPricing.getTotals().getMarketing_fee().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 :
                        new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .build();
    }
}
