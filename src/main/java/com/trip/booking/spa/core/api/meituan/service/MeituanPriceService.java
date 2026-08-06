package com.trip.booking.spa.core.api.meituan.service;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;

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
