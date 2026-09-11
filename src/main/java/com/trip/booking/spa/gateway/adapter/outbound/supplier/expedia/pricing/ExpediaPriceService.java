package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CheckPriceResponse;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaPriceService {

    /**
     * 查价。分态由本层判定——供应商答成功但无任何报价才是「确定没有」，
     * 调用失败、非 2xx、响应无法判读一律「未能确认」。
     */
    PricingResult queryPrices(PriceReq request, Supplier supplier);

    List<Product> queryProductPrice(PriceReq request, Supplier supplier);


    List<Product> queryPricesCache(PriceReq request, Supplier supplier);

}
