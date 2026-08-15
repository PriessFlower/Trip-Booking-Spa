package com.trip.booking.spa.legacy.fastpay;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.legacy.fastpay.service.FastPayService;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("FastpayHotelsProductSyncService")
@Slf4j
public class FastpayProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private FastPayService fastPayService;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            return fastPayService.queryProductPrice(priceReq, supplier);
        }
        return fastPayService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> queryPriceResponse) {
        return queryPriceResponse;
    }
}
