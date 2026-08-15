package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;

public interface CheckPriceSyncService {
    CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq);

}
