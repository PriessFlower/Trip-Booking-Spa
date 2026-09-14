package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;

public interface CheckPriceSyncService {
    CheckPriceResult checkPrice(CheckPriceCommand checkPriceReq);

}
