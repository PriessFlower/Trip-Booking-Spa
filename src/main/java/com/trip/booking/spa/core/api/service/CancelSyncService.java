package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.CancelRespDTO;
import com.trip.booking.spa.core.api.request.CancelReq;

public interface CancelSyncService {
    CancelRespDTO cancel(CancelReq cancelReq);
}
