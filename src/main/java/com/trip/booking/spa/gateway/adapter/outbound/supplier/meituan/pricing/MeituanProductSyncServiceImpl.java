package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 美团查价能力入口。bean 名必须是 {@code meituanProductSyncService}
 * （SupplierSourceEnum.MEITUAN.desc + Capability.PRICING 后缀），否则路由不到。
 */
@Service("meituanProductSyncService")
public class MeituanProductSyncServiceImpl extends AbstractProductSyncSupportService {

    @Resource
    private MeituanPriceService meituanPriceService;

    @Override
    public PricingResult querySupplierPrice(PriceQuery priceReq) {
        // 上游绕过缓存直接问现价这条路：上游请求在等，限流拿不到即如实失败
        return meituanPriceService.queryPrices(priceReq, CallPurpose.LIVE);
    }
}
