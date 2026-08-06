package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.CheckPriceRespDTO;
import com.trip.booking.spa.cli.seq.CheckPriceReq;

public interface CheckPriceSyncService {
    CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq);

}
