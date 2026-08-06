package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;

import java.util.List;

public interface ProductSyncService {

    List<ProductRespDTO> queryPrice(PriceReq priceReq, Supplier supplier);
}
