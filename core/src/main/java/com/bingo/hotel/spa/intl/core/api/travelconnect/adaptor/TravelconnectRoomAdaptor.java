package com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor;

import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.enums.BedTypeSupplierEnum;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import org.apache.commons.compress.utils.Lists;

import java.util.Arrays;
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
            request.setArea(roomVo.getAllotment() + "");
            request.setBedInfoList(convertBeds(roomVo.getBedtypes()));
            list.add(request);
        });
        return list;
    }

    private static List<List<BedInfoDTO>> convertBeds(List<SearchResponse.DataBean.HoteldetailBean.RoomsBean.BedtypesBean> beds) {
        List<List<BedInfoDTO>> result = Lists.newArrayList();
        beds.forEach(bed -> {
            List<BedInfoDTO> bedInfoDTOS = Lists.newArrayList();
            Arrays.stream(bed.getBedtype().split(",")).forEach(s -> {
                BedInfoDTO bedInfoDTO = new BedInfoDTO();
                bedInfoDTO.setBedType(getBedType(s.split("x")[0]));
                bedInfoDTO.setBedNumber(Integer.parseInt(s.split("x")[1]));
                bedInfoDTOS.add(bedInfoDTO);
            });
            result.add(bedInfoDTOS);
        });
        return result;
    }

    private static String getBedType(String bedType) {
        switch (bedType) {
            case "FullBed":
                return BedTypeSupplierEnum.LARGE_BED.getValue() + "";
            case "TwinBed":
            case "TwinXLBed":
                return BedTypeSupplierEnum.TWIN_BED.getValue() + "";
            case "BunkBed":
                return BedTypeSupplierEnum.BUNK_BED.getValue() + "";
            case "QueenBed":
                return BedTypeSupplierEnum.EXTRA_LARGE_BED.getValue() + "";
            case "KingBed":
                return BedTypeSupplierEnum.SUPER_LARGE_BED.getValue() + "";
            case "SofaBed":
                return BedTypeSupplierEnum.SOFA_BED.getValue() + "";
            default:
                return bedType;
        }
    }
}
