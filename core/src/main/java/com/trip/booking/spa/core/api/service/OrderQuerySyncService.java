package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.OrderRespDTO;
import com.trip.booking.spa.cli.seq.OrderQueryReq;

public interface OrderQuerySyncService {
    OrderRespDTO orderQuery(OrderQueryReq orderQueryReq);
}
