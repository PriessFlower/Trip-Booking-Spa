package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;

/** 差旅无忧查价能力的内部接口：出报与刷价共用，验价那几个钩子在实现类上 */
public interface ClwyPriceService {

    /**
     * 查一次现货并转换为可售产品。
     *
     * @param purpose 用途决定吃哪个子桶、拿不到许可是等还是走（{@link CallPurpose}）
     */
    PricingResult queryPrices(PriceQuery request, CallPurpose purpose);
}
