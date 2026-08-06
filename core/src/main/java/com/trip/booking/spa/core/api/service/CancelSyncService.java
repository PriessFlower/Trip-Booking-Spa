package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.CancelRespDTO;
import com.trip.booking.spa.cli.seq.CancelReq;

public interface CancelSyncService {
    CancelRespDTO cancel(CancelReq cancelReq);
}
