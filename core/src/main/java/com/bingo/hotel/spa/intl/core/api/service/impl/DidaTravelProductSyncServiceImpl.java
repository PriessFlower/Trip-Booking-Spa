package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.didatravel.utils.DidaTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Iterator;
import java.util.List;

@Service("didatravelProductSyncService")
public class DidaTravelProductSyncServiceImpl extends AbstractProductSyncSupportService<DidaTravelResponse> {

    @Autowired
    private DidatravelHotelService didatravelHotelService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Autowired
    private DidaTravelProductConvertUtil didaTravelProductConvertUtil;

    @Override
    public DidaTravelResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordDaolvQps();
        DidaTravelResponse response = null;
        if(StringUtils.isNotBlank(supplier.getSProductId())){
            PriceConfirmResponse checkPrice = didatravelHotelService.checkPrice(buildCheckPriceReq(priceReq, supplier), false);
            response = buildResponse(checkPrice);
        } else {
            response = didatravelHotelService.getHotelService(priceReq, supplier.getSHotelId());
        }
        return response;
    }

    private DidaTravelResponse buildResponse(PriceConfirmResponse checkResponse) {
        String jsonString = JSON.toJSONString(checkResponse);
        DidaTravelResponse response = JSONObject.parseObject(jsonString, DidaTravelResponse.class);
        return response;
    }

    public CheckPriceReq buildCheckPriceReq(PriceReq priceReq, Supplier supplier) {
        return CheckPriceReq.builder()
                .checkIn(priceReq.getCheckIn())
                .checkOut(priceReq.getCheckout())
                .sProductId(supplier.getSProductId())
                .sHotelId(supplier.getSHotelId())
                .supplierId(supplier.getSupplierId())
                .roomNum(priceReq.getRoomNum())
                .totalPrice(0)
                .sCityCode(supplier.getSCityCode())
                .adultCount(priceReq.getAdultNum())
//                .planSession()
                .build();
    }

    @Override
    public List<ProductRespDTO> productRespConvert(DidaTravelResponse didaTravelResponse) {
        return didaTravelProductConvertUtil.convertRatePlanVO(didaTravelResponse);
    }
}
