package com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor;

import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class TravelconnectRoomAdaptor {
    public static List<SupplierRoomBaseRequest> transform(SearchResponse searchResponse) {
        List<SupplierRoomBaseRequest> list = Lists.newArrayList();

        searchResponse.getData().getHoteldetail().getRooms().forEach(roomVo -> {
            SupplierRoomBaseRequest request = new SupplierRoomBaseRequest();
            request.setSupplierId(10001);
            request.setSupplierHotelId(searchResponse.getData().getHoteldetail().getHotelcode());
            request.setSupplierRoomId(roomVo.getRoomid());
            request.setSupplierRoomName(roomVo.getRoomname());
            request.setSupplierRoomNameCN(roomVo.getRoomname());
//            request.setBedDesc(roomVo.getBedtypes());
            list.add(request);
        });
        return list;
    }
}
