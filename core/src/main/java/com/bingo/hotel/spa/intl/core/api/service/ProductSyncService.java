package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;

import java.util.List;

public interface ProductSyncService {

    List<ProductRespDTO> queryPrice(PriceReq priceReq, Supplier supplier);
}
