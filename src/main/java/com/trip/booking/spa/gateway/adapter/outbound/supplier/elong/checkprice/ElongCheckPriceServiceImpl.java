package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.ElongPriceService;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceSyncSupportService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 艺龙验价能力入口。bean 名必须是 {@code elongCheckPriceSyncService}
 * （SupplierSourceEnum.ELONG.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 三态判定与 resolve 换票在 {@link ElongPriceService#checkPrices}。
 */
@Service("elongCheckPriceSyncService")
public class ElongCheckPriceServiceImpl extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Resource
    private ElongPriceService elongPriceService;

    @Override
    public CheckPriceRespDTO doCheckPrice(CheckPriceReq checkPriceReq) {
        return elongPriceService.checkPrices(checkPriceReq);
    }

    @Override
    public CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO checkResponse) {
        return checkResponse;
    }
}
