package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service("ratehawkProductSyncService")
@Slf4j
public class RatehawkProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private RateHawkService rateHawkService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            return rateHawkService.queryProductPrice(priceReq, supplier);
        }
        return rateHawkService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> queryPriceResponse) {
        return queryPriceResponse;
    }
}
