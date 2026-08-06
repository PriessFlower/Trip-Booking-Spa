package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractCheckPriceSyncSupportService<T> implements CheckPriceSyncService {


    @Override
    public CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq) {
        try {
            T t = doCheckPrice(checkPriceReq);

            log.info("CheckPriceSyncService checkPriceReq : {} checkResp : {}",
                    JsonUtils.writeObject2Json(checkPriceReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("CheckPriceSyncService doCheckPrice is null checkPriceReq : {}",
                        JsonUtils.writeObject2Json(checkPriceReq));
                return null;
            }

            CheckPriceRespDTO respDTO = checkPriceRespConvert(t);

            if (respDTO == null) {
                log.error("CheckPriceSyncService checkPriceRespConvert is null T : {}", JsonUtils.writeObject2Json(t));
            }
            return respDTO;
        } catch (Exception e) {
            log.error("CheckPriceSyncService is error e:", e);
            return null;
        }
    }

    public abstract T doCheckPrice(CheckPriceReq checkPriceReq);

    public abstract CheckPriceRespDTO checkPriceRespConvert(T t);


}
