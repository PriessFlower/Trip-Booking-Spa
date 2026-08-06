package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
import com.trip.booking.spa.cli.seq.CheckPriceReq;
import com.trip.booking.spa.cli.seq.PriceReq;
import com.trip.booking.spa.cli.seq.Supplier;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityResult;
import com.trip.booking.spa.core.api.huitravel.service.HuiTravelService;
import com.trip.booking.spa.core.api.huitravel.utils.HuiTravelProductConvertUtil;
import com.trip.booking.spa.core.api.service.AbstractProductSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import com.trip.booking.spa.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service("huitravelProductSyncService")
@Slf4j
public class HuiTravelProductSyncServiceImpl extends AbstractProductSyncSupportService<AvailabilityResponse> {

    @Autowired
    private HuiTravelService huiTravelService;
    @Autowired
    private HuiTravelProductConvertUtil util;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public AvailabilityResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordHuiTravelQps();
        //汇智查询时如果带儿童直接过滤掉，汇智不支持儿童政策。
        if (priceReq.getChildNum() > 0 || priceReq.getChildAges().size() > 0) {
            return null;
        }
        if (StringUtils.isEmpty(supplier.getSProductId())) {
            //汇智不能超过30天的查询
            if (!DateUtil.dateBefore(priceReq.getCheckIn(), DateUtil.addDay(new Date(), 30))) {
                AvailabilityResponse response = new AvailabilityResponse();
                AvailabilityResult result = new AvailabilityResult();
                response.setResult(result);
                return response;
            } else {
                return huiTravelService.getPrice(priceReq, supplier.getSHotelId());
            }
        } else {
            return huiTravelService.getPriceByProductId(CheckPriceReq.builder()
                    .checkIn(priceReq.getCheckIn())
                    .checkOut(priceReq.getCheckout())
                    .sProductId(supplier.getSProductId())
                    .sHotelId(supplier.getSHotelId())
                    .roomNum(priceReq.getRoomNum())
                    .supplierId(supplier.getSupplierId())
                    .adultCount(priceReq.getAdultNum())
                    .totalPrice(0)
                    .build());
        }
    }

    @Override
    public List<ProductRespDTO> productRespConvert(AvailabilityResponse searchResponse) {
        if (searchResponse.getCheckResponse() != null) {
            return util.convertRatePlanCheckVO(searchResponse);
        }
        return util.convertRatePlanVO(searchResponse);
    }
}
