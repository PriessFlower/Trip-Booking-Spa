package com.bingo.hotel.spa.intl.core.api.huitravel.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.single.SingleHotelResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.HotelDetail;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class HuiTravelHotelAdaptor {
    public static List<SupplierHotelBaseRequest> transform(HotelDetail hotelDetail) {
        List<SupplierHotelBaseRequest> list = Lists.newArrayList();

        SupplierHotelBaseRequest request = new SupplierHotelBaseRequest();
        request.setSupplierId(10004);
        request.setSupplierHotelId(hotelDetail.getHid()+ "");
        request.setSupplierHotelName(hotelDetail.getName());
        request.setSupplierHotelNameCN(hotelDetail.getEn_name());
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
