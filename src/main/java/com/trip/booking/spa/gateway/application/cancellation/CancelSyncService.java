package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;

public interface CancelSyncService {
    CancelRespDTO cancel(CancelReq cancelReq);
}
