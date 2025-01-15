package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.meituan.service.MeituanPriceService;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
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
