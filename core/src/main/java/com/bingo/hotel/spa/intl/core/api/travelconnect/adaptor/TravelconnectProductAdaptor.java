package com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class TravelconnectProductAdaptor {
    public static List<SupplierProductBaseRequest> transform(SearchResponse searchResponse) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();

        searchResponse.getData().getHoteldetail().getRooms().forEach(roomVo -> {
            SupplierProductBaseRequest request = new SupplierProductBaseRequest();
            request.setSupplierId(10001);
            request.setSupplierHotelId(searchResponse.getData().getHoteldetail().getHotelcode());
            request.setSupplierRoomId(roomVo.getRoomid());
            request.setSupplierProductName(roomVo.getRoomname());
            request.setBreakfast(roomVo.isIncludebreakfast() ? 1 : 0);
            request.setCancelType(0);
            request.setSupplierProductId(buildProductId(roomVo.getRoomid(),request.getBreakfast(),roomVo.getAdultcount(),roomVo.getChildcount()));
            list.add(request);
        });
        return list;
    }

    private static String buildProductId(String roomId, Integer breakfast, int adultCount, int childCount) {
        return roomId + "_" + breakfast + "_" + adultCount + "_" + childCount;
    }
}
