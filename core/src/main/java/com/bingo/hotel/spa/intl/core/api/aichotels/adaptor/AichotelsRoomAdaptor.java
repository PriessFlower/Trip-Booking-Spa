package com.bingo.hotel.spa.intl.core.api.aichotels.adaptor;

import com.bingo.hotel.info.intl.cli.request.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;

public class AichotelsRoomAdaptor {
    public static List<SupplierRoomBaseRequest> transform(RoomInfoResponse roomInfoResponse) {
        List<SupplierRoomBaseRequest> list = Lists.newArrayList();

        roomInfoResponse.getRoom_list().forEach(roomVo -> {
            SupplierRoomBaseRequest request = new SupplierRoomBaseRequest();
            request.setSupplierId(10002);
//            request.setSupplierHotelId(searchResponse.getData().getHoteldetail().getHotelcode());
            request.setSupplierRoomId(roomVo.getRoom_type());
            request.setSupplierRoomName(roomVo.getRoom_name());
            request.setSupplierRoomNameCN(roomVo.getRoom_name_zh());
            request.setArea(roomVo.getRoom_size());
            request.setBedInfoList(convertBed((List<Map<String, String>>) roomVo.getBed_info()));
            request.setHasWindows(roomVo.getWindow() == -1 ? 2 : roomVo.getWindow());
//            request.setBedDesc(roomVo.getBedtypes());
            list.add(request);
        });
        return list;
    }


    private static List<List<BedInfoDTO>> convertBed(List<Map<String, String>> beds) {
        List<List<BedInfoDTO>> list = Lists.newArrayList();
        for (Map<String, String> bed : beds) {
            List<BedInfoDTO> bedInfoDTOS = Lists.newArrayList();
            bed.forEach((k, v) -> {
                BedInfoDTO bedInfoDTO = new BedInfoDTO();
                bedInfoDTO.setBedType(1);
                bedInfoDTO.setNum(Integer.parseInt(v));
                bedInfoDTOS.add(bedInfoDTO);
            });
            list.add(bedInfoDTOS);
        }
        return list;
    }
}
