package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;

import java.util.List;

/**
 * @description:缓存处理
 * @author: dick_w
 * @date: 2025/3/12 10:20
 * @param:
 * @return:
 **/
public interface CachePriceService {

    List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier);

    void productToCache(List<ProductRespDTO> respDTOS, PriceReq request);

}
