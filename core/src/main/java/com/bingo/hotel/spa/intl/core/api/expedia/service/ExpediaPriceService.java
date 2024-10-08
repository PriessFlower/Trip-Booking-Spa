package com.bingo.hotel.spa.intl.core.api.expedia.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.CheckPriceResponse
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaPriceService {

    List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier);

    List<ProductRespDTO> queryProductPrice(CheckPriceReq request);

    CheckPriceResponse checkPrices(CheckPriceReq request);

}
