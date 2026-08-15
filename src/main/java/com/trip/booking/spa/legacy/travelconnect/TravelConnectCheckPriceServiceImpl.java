package com.trip.booking.spa.legacy.travelconnect;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
import com.trip.booking.spa.legacy.travelconnect.bean.prebook.response.PrebookResponse;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.legacy.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.legacy.travelconnect.utils.TravelConnectProductConvertUtil;
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
                .outcome(CheckPriceOutcome.BOOKABLE)
                .salePrice((int) searchResponse.getPrebookResponse().getData().getTotal() * 100)
                .plansId(searchResponse.getPlansId())
                .bedTypeCode(searchResponse.getPrebookResponse().getData().getBedtypes().stream().map(PrebookResponse.DataBean.BedtypesBean::getBedtypeid).collect(Collectors.toList()))
                .build();
    }
}
