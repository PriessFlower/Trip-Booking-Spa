package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 差旅无忧查价能力入口。bean 名必须是 {@code clwyProductSyncService}
 * （SupplierSourceEnum.CLWY.desc + Capability.PRICING 后缀），否则路由不到。
 */
@Service("clwyProductSyncService")
public class ClwyProductSyncServiceImpl extends AbstractProductSyncSupportService {

    @Resource
    private ClwyPriceService clwyPriceService;

    @Override
    public PricingResult querySupplierPrice(PriceQuery priceReq) {
        // 上游绕过缓存直接问现价这条路：上游请求在等，限流拿不到即如实失败
        return clwyPriceService.queryPrices(priceReq, CallPurpose.LIVE);
    }
}
