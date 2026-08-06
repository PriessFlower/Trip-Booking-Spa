package com.trip.booking.spa.core.api.huitravel.adaptor;

import com.trip.booking.spa.core.placeholder.hotelinfo.request.SupplierProductBaseRequest;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class HuiTravelProductAdaptor {
    public static List<SupplierProductBaseRequest> transform(AvailabilityResponse searchResponse, String supplierHotelId) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();

        searchResponse.getResult().getPrices().forEach(roomListBean -> {
                SupplierProductBaseRequest request = new SupplierProductBaseRequest();
                request.setSupplierId(10004);
                request.setSupplierHotelId(supplierHotelId);
                request.setSupplierRoomId(roomListBean.getRid()+"");
                request.setSupplierProductId(roomListBean.getRpid()+"");
                request.setSupplierProductName(roomListBean.getName());
                request.setBreakfast(roomListBean.getBreakfast_count());
                request.setCancelType(0);
                list.add(request);
        });
        return list;
    }
}
