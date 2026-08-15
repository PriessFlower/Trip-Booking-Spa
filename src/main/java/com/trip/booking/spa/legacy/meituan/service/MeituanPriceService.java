package com.trip.booking.spa.legacy.meituan.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;

import java.util.List;

/**
 * 价格相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2025/01/09
 * @since : 1.0
 **/
public interface MeituanPriceService {

    List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier);

    List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier);

    CheckPriceRespDTO checkPrices(CheckPriceReq request);
}
