package com.trip.booking.spa.legacy.travelconnect.adaptor;

import com.trip.booking.spa.legacy.placeholder.hotelbase.enums.BedTypeExpediaEnum;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.dto.BedInfoDTO;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierRoomBaseRequest;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
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
            case "QueenBed":
            case "DoubleBed":
                return BedTypeExpediaEnum.QUEEN_BED.getValue() + "";
            case "TwinBed":
            case "TwinXLBed":
                return BedTypeExpediaEnum.FULL_BED.getValue() + "";
            case "BunkBed":
                return BedTypeExpediaEnum.BUNK_BED.getValue() + "";
            case "KingBed":
                return BedTypeExpediaEnum.KING_BED.getValue() + "";
            case "SofaBed":
                return BedTypeExpediaEnum.SOFA_BED.getValue() + "";
            default:
                return bedType;
        }
    }
}
