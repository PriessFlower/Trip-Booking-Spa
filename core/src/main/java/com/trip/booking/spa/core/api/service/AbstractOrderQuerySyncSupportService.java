package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.OrderRespDTO;
import com.trip.booking.spa.cli.seq.OrderQueryReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractOrderQuerySyncSupportService<T> implements OrderQuerySyncService {

    @Override
    public OrderRespDTO orderQuery(OrderQueryReq orderQueryReq) {
        try {
            T t = doOrderQuery(orderQueryReq);

            log.info("OrderQuerySyncService orderQueryReq : {}, orderQueryResp:{}", JsonUtils.writeObject2Json(orderQueryReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("OrderQuerySyncService doOrderQuery is null orderQueryReq : {}", JsonUtils.writeObject2Json(orderQueryReq));
                return null;
            }

            OrderRespDTO orderQueryRespDTO = orderQueryRespConvert(t);

            if (orderQueryRespDTO == null) {
                log.error("OrderQuerySyncService orderQueryRespConvert is null T : {}", JsonUtils.writeObject2Json(t));
            }

            return orderQueryRespDTO;
        } catch (Exception e) {
            log.error("OrderQuerySyncService is error e:", e);
            return null;
        }
    }

    public abstract T doOrderQuery(OrderQueryReq orderQueryReq);

    public abstract OrderRespDTO orderQueryRespConvert(T t);

}
