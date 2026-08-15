package com.trip.booking.spa.legacy.huitravel.adaptor;

import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierHotelBaseRequest;
import com.trip.booking.spa.legacy.aichotels.bean.hotel.single.SingleHotelResponse;
import com.trip.booking.spa.legacy.huitravel.bean.hotel.detail.HotelDetail;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class HuiTravelHotelAdaptor {
    public static List<SupplierHotelBaseRequest> transform(HotelDetail hotelDetail) {
        List<SupplierHotelBaseRequest> list = Lists.newArrayList();

        SupplierHotelBaseRequest request = new SupplierHotelBaseRequest();
        request.setSupplierId(10004);
        request.setSupplierHotelId(hotelDetail.getHid()+ "");
        request.setSupplierHotelName(hotelDetail.getEn_name());
        request.setSupplierHotelNameCN(hotelDetail.getName());
        request.setAddress(hotelDetail.getAddress());
        request.setAddressCN(hotelDetail.getAddress());
        request.setCountryCode(hotelDetail.getCountry_code()+"");
        request.setCountryName(hotelDetail.getCountry());
        request.setCityId(hotelDetail.getCity_code()+"");
        request.setCityName(hotelDetail.getCity());
        request.setCityNameCN(hotelDetail.getCity());
        request.setTelephone(hotelDetail.getTel());
        request.setLatitude(hotelDetail.getLatitude());
        request.setLongitude(hotelDetail.getLongitude());
        request.setRecommendLevel(Integer.parseInt(hotelDetail.getStar()));
        list.add(request);
        return list;
    }
}
