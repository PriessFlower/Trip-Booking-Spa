package com.trip.booking.spa.legacy.ratehawk.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;

import java.util.List;

/**
 * RateHawk静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/06
 * @since : 1.0
 **/
public interface RateHawkService {

    void queryAndSaveStaticInfo(boolean downloadFlag);

    void queryAndSaveProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds, Integer startNum);

    List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier);

    List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier);

    CheckPriceRespDTO checkPrices(CheckPriceReq request);

    List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier);
}
