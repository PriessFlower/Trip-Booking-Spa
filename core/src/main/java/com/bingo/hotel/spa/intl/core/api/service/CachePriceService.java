package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;

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

    void productToCache(List<ProductRespDTO> respDTOS);

}
