package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.OrderRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.OrderQueryReq;

public interface OrderQuerySyncService {
    OrderRespDTO orderQuery(OrderQueryReq orderQueryReq);
}
