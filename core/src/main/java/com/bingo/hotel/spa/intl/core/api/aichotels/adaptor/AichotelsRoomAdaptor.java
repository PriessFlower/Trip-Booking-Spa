package com.bingo.hotel.spa.intl.core.api.aichotels.adaptor;


import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room.RoomInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;

@Slf4j
public class AichotelsRoomAdaptor {
    public static List<SupplierRoomBaseRequest> transform(RoomInfoResponse roomInfoResponse, String hotelId) {
        List<SupplierRoomBaseRequest> list = Lists.newArrayList();

        roomInfoResponse.getRoom_list().forEach(roomVo -> {
            SupplierRoomBaseRequest request = new SupplierRoomBaseRequest();
            request.setSupplierId(10002);
            request.setSupplierHotelId(hotelId);
            request.setSupplierRoomId(roomVo.getRoom_type());
            request.setSupplierRoomName(roomVo.getRoom_name());
            request.setSupplierRoomNameCN(roomVo.getRoom_name_zh());
            request.setArea(roomVo.getRoom_size());
            request.setCapacity(roomVo.getMax_occupancy() != null ? roomVo.getMax_occupancy().getMax_adults() : 1);
            request.setBedInfoList(convertBed((List<Map<String, Integer>>) roomVo.getBed_info()));
            request.setHasWindows(roomVo.getWindow() == -1 ? 2 : roomVo.getWindow());
            if (roomVo.getNonsmoking() != null) {
                switch (roomVo.getNonsmoking()) {
                    case 0:
                        request.setIsSmoking(1);
                        break;
                    case 1:
                        request.setIsSmoking(0);
                        break;
                    case 2:
                        request.setIsSmoking(2);
                        break;
                }
            } else {
                request.setIsSmoking(0);
            }
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
