package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaPriceService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service("expediaProductSyncService")
@Slf4j
public class ExpediaProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        List<ProductRespDTO> queryPriceResponse = expediaPriceService.queryPrices(priceReq, supplier);
        if (StringUtils.isBlank(supplier.getSProductId())) {
            return queryPriceResponse;
        }
        return expediaPriceService.queryProductPrice(CheckPriceReq.builder()
                .checkIn(priceReq.getCheckIn())
                .checkOut(priceReq.getCheckout())
                .sProductId(supplier.getSProductId())
                .sHotelId(supplier.getSHotelId())
                .roomNum(priceReq.getRoomNum())
                .supplierId(supplier.getSupplierId())
                .adultCount(priceReq.getAdultNum())
                .totalPrice(0)
                .build());
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> queryPriceResponse) {
        return queryPriceResponse;
    }
}
