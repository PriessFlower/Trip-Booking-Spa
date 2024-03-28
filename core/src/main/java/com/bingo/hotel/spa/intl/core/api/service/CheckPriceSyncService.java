package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;

public interface CheckPriceSyncService {
    CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq);

}
