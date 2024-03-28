package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;

import java.util.List;

/**
 * @Description 缓存处理
 * @Author lihao
 * @Date 2024/1/10 19:06
 **/
public interface CachePriceService {

    List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier);

    void productToCache(List<ProductRespDTO> respDTOS);

    void productToCacheNoDown(List<ProductRespDTO> respDTOS);

}
