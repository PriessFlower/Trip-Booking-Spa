package com.bingo.hotel.spa.intl.core.api.meituan.service;

/**
 * 价格相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2025/01/09
 * @since : 1.0
 **/
public interface MeituanPriceService {

    void queryPrices(Long maxId, Integer pageSize);

    void queryPrice(Long maxId, Integer pageSize);

    void checkPrice(Integer pageNumber, Integer pageSize, String type);
}
