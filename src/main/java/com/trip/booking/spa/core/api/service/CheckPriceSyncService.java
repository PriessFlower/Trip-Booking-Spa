package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;

public interface CheckPriceSyncService {
    CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq);

}
