package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;

import java.util.List;

/**
 * 艺龙协议逻辑：查价（hotel.detail）与验价（hotel.data.validate）。
 * 验价与查价同住一处，因为验价的第一步就是重打一次查价（现取现验，R-3.1）。
 */
public interface ElongPriceService {

    /**
     * 查价。分态由本层判定——只有供应商明确回答无在售（{@code isEmptyResult}）才是
     * 「确定没有」，调用失败、业务错误码、凭据缺失一律「未能确认」。
     */
    PricingResult queryPrices(PriceReq request, Supplier supplier, CallPurpose purpose);

}
