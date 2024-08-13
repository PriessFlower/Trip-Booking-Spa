package com.bingo.hotel.spa.intl.core.api.huitravel.adaptor;


import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.Room;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;

@Slf4j
public class HuiTravelRoomAdaptor {
    public static List<SupplierRoomBaseRequest> transform(List<Room> room, String hotelId) {
        List<SupplierRoomBaseRequest> list = Lists.newArrayList();

        room.forEach(roomVo -> {
            SupplierRoomBaseRequest request = new SupplierRoomBaseRequest();
            request.setSupplierId(10004);
            request.setSupplierHotelId(hotelId);
            request.setSupplierRoomId(roomVo.getRid() + "");
            request.setSupplierRoomName(roomVo.getEn_name());
            request.setSupplierRoomNameCN(roomVo.getName());
            request.setArea(roomVo.getArea());
            request.setCapacity(roomVo.getMax_occupancy());
//            request.setBedInfoList(convertBed((List<Map<String, Integer>>) roomVo.getBed_info()));
            request.setHasWindows(roomVo.getWindow_type() == 0 ? 2 : roomVo.getWindow_type());
            request.setIsSmoking(0);
            list.add(request);
        });
        return list;
    }


    private static List<List<BedInfoDTO>> convertBed(List<Map<String, Integer>> beds) {
        List<List<BedInfoDTO>> list = Lists.newArrayList();
        if (beds == null) {
            return null;
        }
        for (Map<String, Integer> bed : beds) {
            List<BedInfoDTO> bedInfoDTOS = Lists.newArrayList();
            bed.forEach((k, v) -> {
                BedInfoDTO bedInfoDTO = new BedInfoDTO();
                switch (k) {
                    case "K":
                        bedInfoDTO.setBedType("8");
                        break;
                    case "Q":
                        bedInfoDTO.setBedType("1");
                        break;
                    case "D":
                        bedInfoDTO.setBedType("25");
                        break;
                    case "T":
                        bedInfoDTO.setBedType("3");
                        break;
                    default:
                        bedInfoDTO.setBedType("0");
                        break;
                }

                bedInfoDTO.setBedNumber(v);
                bedInfoDTOS.add(bedInfoDTO);
            });
            list.add(bedInfoDTOS);
        }
        return list;
    }
}
