package com.bingo.hotel.spa.intl.core.api.ratehawk.service;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;

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
