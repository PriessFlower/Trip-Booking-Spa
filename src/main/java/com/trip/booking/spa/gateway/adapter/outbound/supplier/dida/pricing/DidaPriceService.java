package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;

/**
 * 道旅查价。两路消费方（上游实时查价 / 后台刷价）共用这一段，用途由 {@link CallPurpose}
 * 声明，通道层据此扣用途桶。
 */
public interface DidaPriceService {

    PricingResult queryPrices(PriceQuery request, CallPurpose purpose);
}
