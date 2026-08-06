package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;

import java.util.List;

public interface ProductSyncService {

    List<ProductRespDTO> queryPrice(PriceReq priceReq, Supplier supplier);
}
