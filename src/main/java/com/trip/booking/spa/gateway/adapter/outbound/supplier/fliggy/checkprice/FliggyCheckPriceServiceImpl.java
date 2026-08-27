package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.FliggyPriceServiceImpl;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 飞猪验价能力（现取现验，流程在 {@link FliggyPriceServiceImpl#checkPrices}——
 * 查价与验价对分态的口径必须同源）。
 */
@Service("fliggyCheckPriceSyncService")
public class FliggyCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Resource
    private FliggyPriceServiceImpl fliggyPriceService;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return fliggyPriceService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO dto) {
        return dto;
    }
}
