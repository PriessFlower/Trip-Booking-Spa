package com.trip.booking.spa.legacy.aichotels;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.legacy.aichotels.service.AichotelsHotelService;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
import com.trip.booking.spa.legacy.travelconnect.bean.prebook.response.PrebookResponse;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.legacy.travelconnect.service.TravelconnectHotelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service("aicHotelsCheckPriceSyncService")
@Slf4j
public class AicHotelsCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<PreBookResponse> {

    @Autowired
    private AichotelsHotelService aichotelsHotelService;
    @Resource(name="redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public PreBookResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        redisRecordLogServiceImpl.recordAichotelsQps();
        return aichotelsHotelService.checkPrice(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(PreBookResponse searchResponse) {
        if (searchResponse.getResult().getReturn_status().getSuccess().equals("false")) {
            return CheckPriceRespDTO.builder()
                    .outcome(CheckPriceOutcome.INDETERMINATE)
                    .message(searchResponse.getResult().getReturn_status().getException())
                    .build();
        }
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .totalPriceAfter((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .totalPriceBefore((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_before_tax()) * 100))
                .salePrice((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .subPrice((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .message(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getCurrency())
                .build();
    }
}
