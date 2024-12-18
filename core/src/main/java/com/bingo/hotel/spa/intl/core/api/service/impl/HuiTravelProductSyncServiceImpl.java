package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.service.HuiTravelService;
import com.bingo.hotel.spa.intl.core.api.huitravel.utils.HuiTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service("huitravelProductSyncService")
@Slf4j
public class HuiTravelProductSyncServiceImpl extends AbstractProductSyncSupportService<AvailabilityResponse> {

    @Autowired
    private HuiTravelService huiTravelService;
    @Autowired
    private HuiTravelProductConvertUtil util;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public AvailabilityResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordAichotelsQps();
        if (StringUtils.isEmpty(supplier.getSProductId())) {
            return huiTravelService.getPrice(priceReq, supplier.getSHotelId());
        } else {
            return huiTravelService.getPriceByProductId(CheckPriceReq.builder()
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
    }

    @Override
    public List<ProductRespDTO> productRespConvert(AvailabilityResponse searchResponse) {
        if (searchResponse.getCheckResponse() != null) {
            return util.convertRatePlanCheckVO(searchResponse);
        }
        return util.convertRatePlanVO(searchResponse);
    }
}
