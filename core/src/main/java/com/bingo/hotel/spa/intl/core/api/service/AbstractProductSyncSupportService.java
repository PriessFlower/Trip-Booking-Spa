package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Slf4j
public abstract class AbstractProductSyncSupportService<T> implements ProductSyncService {

    @Override
    public List<ProductRespDTO> queryPrice(PriceReq priceReq, Supplier supplier) {
        try {
            T t = querySupplierPrice(priceReq, supplier);

            if (t == null) {
                log.error("ProductSyncService querySupplierPrice is null priceReq : {}, supplier : {}",
                        JsonUtils.writeObject2Json(priceReq), JsonUtils.writeObject2Json(supplier));
                return null;
            }

            log.info("ProductSyncService priceReq : {},supplier : {}, productResp : {}",
                    JsonUtils.writeObject2Json(priceReq),
                    JsonUtils.writeObject2Json(supplier),
                    JsonUtils.writeObject2Json(t));

            List<ProductRespDTO> list = productRespConvert(t);

            if (CollectionUtils.isEmpty(list)) {
                log.error("ProductSyncService productRespConvert is null T : {}", JsonUtils.writeObject2Json(t));
            }
            return list;
        } catch (Exception e) {
            log.error("ProductSyncService is error e:{}", e);
            return null;
        }

    }

    public abstract T querySupplierPrice(PriceReq priceReq, Supplier supplier);

    public abstract List<ProductRespDTO> productRespConvert(T t);

}
