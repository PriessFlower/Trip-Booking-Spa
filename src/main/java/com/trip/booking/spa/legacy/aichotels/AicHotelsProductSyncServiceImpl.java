package com.trip.booking.spa.legacy.aichotels;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.legacy.aichotels.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.legacy.aichotels.service.AichotelsHotelService;
import com.trip.booking.spa.legacy.aichotels.utils.AichotelsProductConvertUtil;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service("aicHotelsProductSyncService")
@Slf4j
public class AicHotelsProductSyncServiceImpl extends AbstractProductSyncSupportService<AvailabilityResponse> {

    @Autowired
    private AichotelsHotelService aichotelsHotelService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Autowired
    private AichotelsProductConvertUtil aichotelsProductConvertUtil;

    @Override
    public AvailabilityResponse querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordAichotelsQps();
        if (StringUtils.isEmpty(supplier.getSProductId())) {
            return aichotelsHotelService.getHotelPrice(priceReq, supplier.getSHotelId());
        } else {
            PreBookResponse preBookResponse = aichotelsHotelService.checkPrice(CheckPriceReq.builder()
                    .checkIn(priceReq.getCheckIn())
                    .checkOut(priceReq.getCheckout())
                    .sProductId(supplier.getSProductId())
                    .sHotelId(supplier.getSHotelId())
                    .roomNum(priceReq.getRoomNum())
                    .supplierId(supplier.getSupplierId())
                    .adultCount(priceReq.getAdultNum())
                    .totalPrice(0)
                    .build());
            preBookResponse.setCheckIn(priceReq.getCheckIn());
            preBookResponse.setCheckOut(priceReq.getCheckout());
            AvailabilityResponse availabilityResponse = new AvailabilityResponse();
            availabilityResponse.setPreBookResponse(preBookResponse);
            availabilityResponse.setHotelCode(supplier.getSHotelId());
            return availabilityResponse;
        }

    }

    @Override
    public List<ProductRespDTO> productRespConvert(AvailabilityResponse searchResponse) {
        if (searchResponse.getPreBookResponse() != null) {
            return aichotelsProductConvertUtil.convertRatePlanCheckVO(searchResponse.getPreBookResponse(), searchResponse.getHotelCode());
        }
        return aichotelsProductConvertUtil.convertRatePlanVO(searchResponse);
    }
}
