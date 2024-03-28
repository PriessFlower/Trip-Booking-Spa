package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.utils.TravelConnectProductConvertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("travelConnectProductSyncService")
@Slf4j
public class TravelConnectProductSyncServiceImpl extends AbstractProductSyncSupportService<SearchResponse> {

    @Autowired
    private TravelconnectHotelService travelconnectHotelService;

    @Override
    public SearchResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        if (StringUtils.isNotEmpty(supplier.getSProductId())) {
            return travelconnectHotelService.checkPrice(priceReq, supplier.getSHotelId(), supplier.getSProductId());
        }
        return travelconnectHotelService.getHotelPrice(priceReq, supplier.getSHotelId());
    }

    @Override
    public List<ProductRespDTO> productRespConvert(SearchResponse searchResponse) {
        return TravelConnectProductConvertUtil.convertRatePlanVO(searchResponse);
    }
}
