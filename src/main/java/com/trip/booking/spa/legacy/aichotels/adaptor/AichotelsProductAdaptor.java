package com.trip.booking.spa.legacy.aichotels.adaptor;

import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierProductBaseRequest;
import com.trip.booking.spa.legacy.aichotels.bean.price.availability.AvailabilityResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class AichotelsProductAdaptor {
    public static List<SupplierProductBaseRequest> transform(AvailabilityResponse searchResponse, String supplierHotelId) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();

        searchResponse.getRoom_list().forEach(roomListBean -> {
            roomListBean.getRates_and_cancellation_policies().forEach(policiesBean -> {
                SupplierProductBaseRequest request = new SupplierProductBaseRequest();
                request.setSupplierId(10002);
                request.setSupplierHotelId(supplierHotelId);
                request.setSupplierRoomId(roomListBean.getRoom_type());
                request.setSupplierProductId(policiesBean.getRoom_key());
                request.setSupplierProductName(roomListBean.getRoom_name());
                if (policiesBean.getBreakfast().getInclude() == 0) {
                    request.setBreakfast(0);
                } else {
                    request.setBreakfast(policiesBean.getBreakfast().getInclude());
                }
                request.setCancelType(0);
                list.add(request);
            });
        });
        return list;
    }
}
