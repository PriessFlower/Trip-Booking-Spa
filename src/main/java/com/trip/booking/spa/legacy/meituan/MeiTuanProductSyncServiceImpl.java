package com.trip.booking.spa.legacy.meituan;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.legacy.meituan.service.MeituanPriceService;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("meituanProductSyncService")
@Slf4j
public class MeiTuanProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private MeituanPriceService meituanPriceService;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {

        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            return meituanPriceService.queryProductPrice(priceReq, supplier);
        }
        return meituanPriceService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> productRespDTOList) {
        return productRespDTOList;
    }

}
