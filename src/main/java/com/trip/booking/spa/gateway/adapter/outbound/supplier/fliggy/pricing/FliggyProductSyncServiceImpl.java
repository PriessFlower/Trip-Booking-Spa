package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 飞猪查价能力。bean 名按 {@code <desc><能力后缀>} 约定，注册表凭它接线。
 */
@Service("fliggyProductSyncService")
public class FliggyProductSyncServiceImpl extends AbstractProductSyncSupportService {

    @Resource
    private FliggyPriceServiceImpl fliggyPriceService;

    @Override
    public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        return fliggyPriceService.queryPrices(priceReq, supplier, CallPurpose.LIVE);
    }
}
