package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.cli.dto.CheckPriceRespDTO;
import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.CheckPriceReq;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;
import com.trip.booking.spa.core.api.service.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.core.api.service.AbstractProductSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import com.trip.booking.spa.core.api.travelconnect.bean.prebook.response.PrebookResponse;
import com.trip.booking.spa.core.api.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.core.api.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.core.api.travelconnect.utils.TravelConnectProductConvertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service("travelConnectCheckPriceSyncService")
@Slf4j
public class TravelConnectCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<SearchResponse> {

    @Autowired
    private TravelconnectHotelService travelconnectHotelService;

    @Resource(name="redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public SearchResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        redisRecordLogServiceImpl.recordTravelconnectQps();
        return travelconnectHotelService.checkPrice(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(SearchResponse searchResponse) {
        return CheckPriceRespDTO.builder()
                .checkStatus(true)
                .prebookToken(searchResponse.getPrebookResponse().getData().getPrebookingtoken())
                .salePrice((int) searchResponse.getPrebookResponse().getData().getTotal() * 100)
                .plansId(searchResponse.getPlansId())
                .bedTypeCode(searchResponse.getPrebookResponse().getData().getBedtypes().stream().map(PrebookResponse.DataBean.BedtypesBean::getBedtypeid).collect(Collectors.toList()))
                .build();
    }
}
