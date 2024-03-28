package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.service.AichotelsHotelService;
import com.bingo.hotel.spa.intl.core.api.aichotels.utils.AichotelsProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.utils.TravelConnectProductConvertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("aicHotelsProductSyncService")
@Slf4j
public class AicHotelsProductSyncServiceImpl extends AbstractProductSyncSupportService<AvailabilityResponse> {

    @Autowired
    private AichotelsHotelService aichotelsHotelService;

    @Override
    public AvailabilityResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {

        return aichotelsHotelService.getHotelPrice(priceReq, supplier.getSHotelId());
    }

    @Override
    public List<ProductRespDTO> productRespConvert(AvailabilityResponse searchResponse) {
        return AichotelsProductConvertUtil.convertRatePlanVO(searchResponse);
    }
}
