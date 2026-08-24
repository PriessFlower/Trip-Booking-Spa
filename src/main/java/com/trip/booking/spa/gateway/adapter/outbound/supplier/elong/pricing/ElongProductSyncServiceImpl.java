package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
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
        // 上游绕过缓存直接问现价这条路：上游请求在等，限流拿不到即如实失败
        return elongPriceService.queryPrices(priceReq, supplier, CallPurpose.LIVE);
    }
}
