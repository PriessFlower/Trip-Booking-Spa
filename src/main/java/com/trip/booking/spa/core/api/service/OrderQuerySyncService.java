package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.OrderRespDTO;
import com.trip.booking.spa.core.api.request.OrderQueryReq;

public interface OrderQuerySyncService {
    OrderRespDTO orderQuery(OrderQueryReq orderQueryReq);
}
