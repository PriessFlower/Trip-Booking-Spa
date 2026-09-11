package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;

public interface CheckPriceSyncService {
    CheckPriceResult checkPrice(CheckPriceReq checkPriceReq);

}
