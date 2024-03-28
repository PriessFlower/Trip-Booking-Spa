package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.CancelRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CancelReq;

public interface CancelSyncService {
    CancelRespDTO cancel(CancelReq cancelReq);
}
