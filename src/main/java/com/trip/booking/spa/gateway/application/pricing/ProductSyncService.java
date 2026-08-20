package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;

public interface ProductSyncService {

    /**
     * 查价。返回值永不为 null——查不出价时也必须说清是「确定没有」还是「没问出来」，
     * 判据见 {@link com.trip.booking.spa.gateway.domain.booking.PricingOutcome}。
     */
    PricingResult queryPrice(PriceReq priceReq, Supplier supplier);
}
