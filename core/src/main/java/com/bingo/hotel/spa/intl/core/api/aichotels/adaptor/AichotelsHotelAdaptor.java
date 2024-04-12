package com.bingo.hotel.spa.intl.core.api.aichotels.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.single.SingleHotelResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class AichotelsHotelAdaptor {
    public static List<SupplierHotelBaseRequest> transform(SingleHotelResponse singleHotelResponse) {
        List<SupplierHotelBaseRequest> list = Lists.newArrayList();

        SupplierHotelBaseRequest request = new SupplierHotelBaseRequest();
        request.setSupplierId(10002);
        request.setSupplierHotelId(singleHotelResponse.getHotel_id() + "");
        request.setSupplierHotelName(singleHotelResponse.getHotel_data().getName_en());
        request.setSupplierHotelNameCN(singleHotelResponse.getHotel_data().getName());
        request.setAddress(singleHotelResponse.getHotel_data().getAddress_en());
        request.setAddressCN(singleHotelResponse.getHotel_data().getAddress());
        request.setCountryCode(singleHotelResponse.getHotel_data().getCountry_short());
        request.setCountryName(singleHotelResponse.getHotel_data().getCountry_name());
        request.setCityId(singleHotelResponse.getHotel_data().getCity_id());
        request.setCityName(singleHotelResponse.getHotel_data().getCity_en());
        request.setCityNameCN(singleHotelResponse.getHotel_data().getCity());
        request.setTelephone(singleHotelResponse.getHotel_data().getPhone());
        request.setLatitude(singleHotelResponse.getHotel_data().getLatitude());
        request.setLongitude(singleHotelResponse.getHotel_data().getLongitude());
        request.setRecommendLevel(singleHotelResponse.getHotel_data().getStar());
        list.add(request);

        return list;
    }
}
