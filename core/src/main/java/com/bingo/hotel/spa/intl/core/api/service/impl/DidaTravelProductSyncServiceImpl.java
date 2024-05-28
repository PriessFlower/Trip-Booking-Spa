package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.didatravel.utils.DidaTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.service.AbstractProductSyncSupportService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Service("didatravelProductSyncService")
public class DidaTravelProductSyncServiceImpl extends AbstractProductSyncSupportService<DidaTravelResponse> {

    @Autowired
    private DidatravelHotelService didatravelHotelService;

    @Override
    public DidaTravelResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        DidaTravelResponse response = didatravelHotelService.getHotelService(priceReq, supplier.getSHotelId());

        if(StringUtils.isNotBlank(supplier.getSProductId())){
            Iterator<DidaTravelResponse.HotelTypeRatePlan> iterator = response.getSuccess().getPriceDetails().getHotelList().get(0).getRatePlanList().iterator();
            while (iterator.hasNext()) {
                DidaTravelResponse.HotelTypeRatePlan next = iterator.next();
                if(!next.getRatePlanID().equals(supplier.getSProductId())){
                    iterator.remove();
                }
            }
        }
        return response;
    }

    @Override
    public List<ProductRespDTO> productRespConvert(DidaTravelResponse didaTravelResponse) {
        return DidaTravelProductConvertUtil.convertRatePlanVO(didaTravelResponse);
    }
}
