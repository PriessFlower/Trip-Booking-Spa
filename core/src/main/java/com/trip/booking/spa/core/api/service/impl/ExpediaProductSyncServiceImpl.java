package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;
import com.trip.booking.spa.core.api.expedia.service.ExpediaPriceService;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaHelper;
import com.trip.booking.spa.core.api.service.AbstractProductSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service("expediaProductSyncService")
@Slf4j
public class ExpediaProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordExpediaQps();
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            return expediaPriceService.queryProductPrice(priceReq, supplier);
        }
        //泰国及韩国酒店当天入住的全关闭
        if (ExpediaHelper.hotelIdList.contains(priceReq.getSuppliers().get(0).getSHotelId())
                && LocalDate.parse(priceReq.getCheckIn()).equals(LocalDate.now()))
        {
            List<ProductRespDTO> response = Lists.newArrayList();
            return response;
        }
        return expediaPriceService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> queryPriceResponse) {
        return queryPriceResponse;
    }
}
