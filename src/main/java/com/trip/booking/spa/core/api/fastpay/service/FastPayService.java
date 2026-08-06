package com.trip.booking.spa.core.api.fastpay.service;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;

import java.util.List;

/**
 * FastPay静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface FastPayService {

    void saveHotelList(int days, String type);

    List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier);

    List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier);

    CheckPriceRespDTO checkPrices(CheckPriceReq request);
}
