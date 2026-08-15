package com.trip.booking.spa.legacy.didatravel;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.legacy.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.trip.booking.spa.legacy.didatravel.service.DidatravelHotelService;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;

@Service("didatravelCheckPriceSyncService")
public class DidaTravelCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<PriceConfirmResponse> {

    @Autowired
    DidatravelHotelService didatravelHotelService;

    @Resource(name = "redisRecordLogServiceImpl")
    RecordLogService redisRecordLogServiceImpl;

    @Override
    public PriceConfirmResponse doCheckPrice(CheckPriceReq checkPriceReq) {
        redisRecordLogServiceImpl.recordDaolvQps();
        return didatravelHotelService.checkPrice(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(PriceConfirmResponse priceConfirmResponse) {

        PriceConfirmResponse.HotelTypeRatePlan plan = priceConfirmResponse.getSuccess().getPriceDetails().getHotelList().get(0).getRatePlanList().get(0);
        ArrayList<String> list = new ArrayList<>();
        list.add(String.valueOf(plan.getBedType()));

        return CheckPriceRespDTO.builder()
//                .referenceNo(priceConfirmResponse.getSuccess().getPriceDetails().getReferenceNo())
                .outcome(CheckPriceOutcome.BOOKABLE)
                .salePrice(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .totalPriceAfter(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .totalPriceBefore(plan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                .message(plan.getCurrency())
                .bedTypeCode(list)
                .build();
    }
}
