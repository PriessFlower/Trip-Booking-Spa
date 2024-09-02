package com.bingo.hotel.spa.intl.core.api.huitravel.adaptor;


import com.bingo.hotel.base.intl.cli.enums.BedTypeAllEnum;
import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.Room;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
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
            request.setBedInfoList(convertBed(roomVo.getBed_type()));
            request.setHasWindows(roomVo.getWindow_type() == 0 ? 2 : roomVo.getWindow_type());
            request.setIsSmoking(0);
            list.add(request);
        });
        return list;
    }


    private static List<List<BedInfoDTO>> convertBed(String beds) {
        try {
            List<List<BedInfoDTO>> list = Lists.newArrayList();
            if (StringUtils.isEmpty(beds)) {
                return null;
            }
            if (beds.contains("/")) {
                String[] bedInfo = beds.split("/");
                for (String bed : bedInfo) {
                    List<BedInfoDTO> bedInfoDTOSOr = new ArrayList<>();
                    BedInfoDTO bedInfoDTO = new BedInfoDTO();
                    if (beds.contains(" ")) {
                        bedInfoDTO.setBedType(convertBedName(bed.split(" ")[1]));
                        bedInfoDTO.setBedNumber(Integer.parseInt(bed.split(" ")[0]));
                    } else {
                        bedInfoDTO.setBedType(convertBedName(bed));
                        bedInfoDTO.setBedNumber(1);
                    }
                    bedInfoDTOSOr.add(bedInfoDTO);
                    list.add(bedInfoDTOSOr);
                }
            } else if (beds.contains("或")) {
                String[] bedInfo = beds.split("或");
                for (String bed : bedInfo) {
                    List<BedInfoDTO> bedInfoDTOSOr = new ArrayList<>();
                    BedInfoDTO bedInfoDTO = new BedInfoDTO();
                    if (beds.contains(" ")) {
                        bedInfoDTO.setBedType(convertBedName(bed.split(" ")[1]));
                        bedInfoDTO.setBedNumber(Integer.parseInt(bed.split(" ")[0]));
                    } else {
                        bedInfoDTO.setBedType(convertBedName(bed));
                        bedInfoDTO.setBedNumber(1);
                    }
                    bedInfoDTOSOr.add(bedInfoDTO);
                    list.add(bedInfoDTOSOr);
                }
            } else {
                List<BedInfoDTO> bedInfoDTOSOr = new ArrayList<>();
                BedInfoDTO bedInfoDTO = new BedInfoDTO();
                if (beds.contains(" ")) {
                    bedInfoDTO.setBedType(convertBedName(beds.split(" ")[1]));
                    bedInfoDTO.setBedNumber(Integer.parseInt(beds.split(" ")[0]));
                } else {
                    bedInfoDTO.setBedType(convertBedName(beds));
                    bedInfoDTO.setBedNumber(1);
                }
                bedInfoDTOSOr.add(bedInfoDTO);
                list.add(bedInfoDTOSOr);
            }
            return list;
        }catch (Exception e) {
            log.error("HuiTravelRoomAdaptor.convertBed error:{}",beds, e);
            return Collections.emptyList();
        }
    }

    private static String convertBedName(String bedName){
        switch (bedName) {
            case "单床":
            case "单人床":
                return BedTypeAllEnum.SINGLE_BED.getValue() + "";
            case "大床":
                return BedTypeAllEnum.LARGE_BED.getValue() + "";
            case "榻榻米":
                return BedTypeAllEnum.TATAMI.getValue() + "";
            case "特大床":
                return BedTypeAllEnum.EXTRA_LARGE_BED.getValue() + "";
            default:
                return BedTypeAllEnum.OTHER.getValue() + "";
        }
    }

}
