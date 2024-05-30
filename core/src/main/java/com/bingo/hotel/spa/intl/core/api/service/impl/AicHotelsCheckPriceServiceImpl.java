package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.service.AichotelsHotelService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractCheckPriceSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.response.PrebookResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
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
                    .checkStatus(false)
                    .message(searchResponse.getResult().getReturn_status().getException())
                    .build();
        }
        return CheckPriceRespDTO.builder()
                .checkStatus(true)
                .prebookToken(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getRoom_key())
                .totalPriceAfter((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .totalPriceBefore((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_before_tax()) * 100))
                .salePrice((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .subPrice((int) (Double.parseDouble(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getTotal_amount_after_tax()) * 100))
                .message(searchResponse.getRoom_list().get(0).getRates_and_cancellation_policies().get(0).getCurrency())
                .build();
    }
}
