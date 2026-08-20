package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 艺龙查价能力入口。bean 名必须是 {@code elongProductSyncService}
 * （SupplierSourceEnum.ELONG.desc + Capability.PRICING 后缀），否则路由不到。
 */
@Service("elongProductSyncService")
public class ElongProductSyncServiceImpl extends AbstractProductSyncSupportService {

    @Resource
    private ElongPriceService elongPriceService;

    @Override
    public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        return elongPriceService.queryPrices(priceReq, supplier);
    }
}
