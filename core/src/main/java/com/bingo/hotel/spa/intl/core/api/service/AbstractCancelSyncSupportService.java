package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.CancelRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CancelReq;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractCancelSyncSupportService<T> implements CancelSyncService {

    @Override
    public CancelRespDTO cancel(CancelReq cancelReq) {
        try {
            T t = doCancel(cancelReq);

            log.info("CancelSyncService cancelReq : {}, cancelResp:{}", JsonUtils.writeObject2Json(cancelReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("CancelSyncService doCancel is null cancelReq : {}", JsonUtils.writeObject2Json(cancelReq));
                return null;
            }

            CancelRespDTO cancelRespDTO = cancelRespConvert(t);

            if (cancelRespDTO == null) {
                log.error("CancelSyncService cancelRespConvert is null T : {}", JsonUtils.writeObject2Json(t));
            }

            return cancelRespDTO;

        } catch (Exception e) {
            log.error("CancelSyncService is error e:{}", e.toString());
            return null;
        }
    }

    public abstract T doCancel(CancelReq cancelReq);

    public abstract CancelRespDTO cancelRespConvert(T t);

}
