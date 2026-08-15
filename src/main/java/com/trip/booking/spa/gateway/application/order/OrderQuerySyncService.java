package com.trip.booking.spa.gateway.application.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;

public interface OrderQuerySyncService {
    OrderRespDTO orderQuery(OrderQueryReq orderQueryReq);
}
