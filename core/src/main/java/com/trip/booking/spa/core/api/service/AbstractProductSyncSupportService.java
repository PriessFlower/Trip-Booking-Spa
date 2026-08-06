package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public abstract class AbstractProductSyncSupportService<T> implements ProductSyncService {

    @Override
    public List<ProductRespDTO> queryPrice(PriceReq priceReq, Supplier supplier) {
        try {
            long start = System.currentTimeMillis();
            T t = querySupplierPrice(priceReq, supplier);

            if (t == null) {
                log.error("ProductSyncService querySupplierPrice is null priceReq : {}, supplier : {}",
                        JsonUtils.writeObject2Json(priceReq), JsonUtils.writeObject2Json(supplier));
                return null;
            }
            if (10005 == supplier.getSupplierId() && StringUtils.isNotBlank(supplier.getSProductId())) {
                log.info("ProductSyncService priceReq : {},supplier : {},response: {},useTime:{}",
                        JsonUtils.writeObject2Json(priceReq), JsonUtils.writeObject2Json(supplier),
                        JsonUtils.writeObject2Json(t), System.currentTimeMillis() - start);
            }
            List<ProductRespDTO> list = productRespConvert(t);
            if (CollectionUtils.isEmpty(list)) {
//                log.error("ProductSyncService productRespConvert is null,priceReq : {},supplier : {} T : {}", JsonUtils.writeObject2Json(priceReq),
//                        JsonUtils.writeObject2Json(supplier), JsonUtils.writeObject2Json(t));
            }
            return list;
        } catch (Exception e) {
            log.error("ProductSyncService is error e:", e);
            return null;
        }
    }

    public abstract T querySupplierPrice(PriceReq priceReq, Supplier supplier);

    public abstract List<ProductRespDTO> productRespConvert(T t);

}
