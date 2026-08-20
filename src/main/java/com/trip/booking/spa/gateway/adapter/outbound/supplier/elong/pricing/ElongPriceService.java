package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
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
    PricingResult queryPrices(PriceReq request, Supplier supplier);

    /**
     * 查价并把结果写入价格缓存，供刷价任务调用。
     *
     * <p>与 {@link #queryPrices} 的唯一区别是多一步落缓存——查价逻辑本身不分叉，
     * 免得刷价与实时查价的口径漂移（productKey、退改、餐食必须同源）。
     *
     * @return 与 queryPrices 同；调用失败或该店当日无在售时分别为 null / 空列表
     */
    List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier);

    CheckPriceRespDTO checkPrices(CheckPriceReq request);
}
