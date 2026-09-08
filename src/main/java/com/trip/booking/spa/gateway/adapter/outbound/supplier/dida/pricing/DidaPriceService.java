package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;

/**
 * 道旅查价。两路消费方（上游实时查价 / 后台刷价）共用这一段，用途由 {@link CallPurpose}
 * 声明，通道层据此扣用途桶。
 */
public interface DidaPriceService {

    PricingResult queryPrices(PriceReq request, Supplier supplier, CallPurpose purpose);
}
