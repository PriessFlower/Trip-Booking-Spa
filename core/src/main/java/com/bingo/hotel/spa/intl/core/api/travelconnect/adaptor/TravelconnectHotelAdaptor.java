package com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class TravelconnectHotelAdaptor {
    public static List<SupplierHotelBaseRequest> transform(SearchResponse searchResponse) {
        List<SupplierHotelBaseRequest> list = Lists.newArrayList();

        searchResponse.getData().getPagehotellist().getData_list().forEach(hotelVO -> {
            SupplierHotelBaseRequest request = new SupplierHotelBaseRequest();
            request.setSupplierId(10001);
            request.setSupplierHotelId(hotelVO.getHotelcode());
            request.setSupplierHotelName(hotelVO.getHotelengname());
            request.setSupplierHotelNameCN(hotelVO.getHotelname());
            request.setAddress(hotelVO.getAddress().get(0));
            request.setAddressCN(hotelVO.getAddress().get(0));
            request.setCountryCode(hotelVO.getCurrency());
            request.setCityId(searchResponse.getData().getCitycode());
            request.setCityName(searchResponse.getData().getCityname());
            request.setCityNameCN(searchResponse.getData().getCityname());
            request.setLatitude(hotelVO.getLatitude());
            request.setLongitude(hotelVO.getLongitude());
            list.add(request);
        });
        return list;
    }
}
