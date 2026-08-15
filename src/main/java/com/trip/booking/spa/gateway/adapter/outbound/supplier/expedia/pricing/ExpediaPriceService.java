package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CheckPriceResponse;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaPriceService {

    List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier);

    List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier);

    CheckPriceRespDTO checkPrices(CheckPriceReq request);

    List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier);

}
