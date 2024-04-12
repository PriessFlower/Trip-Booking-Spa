package com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.hotel.HotelDetailResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

@Slf4j
public class TravelconnectHotelAdaptor {
    public static List<SupplierHotelBaseRequest> transform(HotelDetailResponse searchResponse) {
        List<SupplierHotelBaseRequest> list = Lists.newArrayList();

        searchResponse.getData().forEach(hotelVO -> {
            try {
                SupplierHotelBaseRequest request = new SupplierHotelBaseRequest();
                request.setSupplierId(10001);
                request.setSupplierHotelId(hotelVO.getHotelcode());
                request.setSupplierHotelName(hotelVO.getHotelengname());
                request.setSupplierHotelNameCN(hotelVO.getHotelname());
//            request.setAddress(hotelVO.getAddress().get(0));
                request.setAddressCN(hotelVO.getAddress().get(0));
                request.setCountryCode(hotelVO.getCountry());
                request.setCityId(hotelVO.getCitycode());
//            request.setCityName(hotelVO.getCity());
                request.setCityNameCN(hotelVO.getCity());
                request.setTelephone(hotelVO.getPhone() != null && hotelVO.getPhone().size() > 0 ? hotelVO.getPhone().get(0) : "");
                request.setLatitude(hotelVO.getLatitude());
                request.setLongitude(hotelVO.getLongitude());
                list.add(request);
            }catch (Exception e){
                log.error("TravelconnectHotelAdaptor.transform error:{}",e);
            }
        });
        return list;
    }
}
