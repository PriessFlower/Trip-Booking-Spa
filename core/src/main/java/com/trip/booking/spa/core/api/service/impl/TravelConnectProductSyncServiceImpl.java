package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;
import com.trip.booking.spa.core.api.service.AbstractProductSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import com.trip.booking.spa.core.api.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.core.api.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.core.api.travelconnect.utils.TravelConnectProductConvertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service("travelConnectProductSyncService")
@Slf4j
public class TravelConnectProductSyncServiceImpl extends AbstractProductSyncSupportService<SearchResponse> {

    @Autowired
    private TravelconnectHotelService travelconnectHotelService;

    @Resource
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public SearchResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordTravelconnectQps();
        return travelconnectHotelService.getHotelPrice(priceReq, supplier.getSHotelId());
    }

    @Override
    public List<ProductRespDTO> productRespConvert(SearchResponse searchResponse) {
        return TravelConnectProductConvertUtil.convertRatePlanVO(searchResponse);
    }
}
