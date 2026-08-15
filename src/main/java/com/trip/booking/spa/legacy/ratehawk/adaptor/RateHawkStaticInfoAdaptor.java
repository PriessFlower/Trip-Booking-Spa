package com.trip.booking.spa.legacy.ratehawk.adaptor;

import com.trip.booking.spa.legacy.placeholder.hotelbase.enums.BedTypeExpediaEnum;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.dto.BedInfoDTO;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierHotelBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierProductBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierRoomBaseRequest;
import com.trip.booking.spa.legacy.ratehawk.bean.response.CancellationInfo;
import com.trip.booking.spa.legacy.ratehawk.bean.response.HotelStaticInfo;
import com.trip.booking.spa.legacy.ratehawk.bean.response.QueryProductResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class RateHawkStaticInfoAdaptor {

    public static SupplierHotelBaseRequest transformInfoHotelReq(HotelStaticInfo hotelStaticInfo) {
        String hotelId = String.valueOf(hotelStaticInfo.getHid());
        SupplierHotelBaseRequest supplierHotelBaseRequest = new SupplierHotelBaseRequest()
                .setSupplierId(10007)
                .setSupplierHotelId(hotelId)
                .setSupplierHotelName(hotelStaticInfo.getName())
                .setSupplierHotelNameCN("")
                .setAddress(hotelStaticInfo.getAddress())
                .setAddressCN("")
                .setCountryCode(hotelStaticInfo.getRegion().getCountry_code())
                .setCityId(String.valueOf(hotelStaticInfo.getRegion().getId()))
                .setCityName(hotelStaticInfo.getRegion().getName())
                .setCityNameCN("")
                .setTelephone(hotelStaticInfo.getPhone())
                .setPostcode(hotelStaticInfo.getPostal_code())
                .setLatitude(String.valueOf(hotelStaticInfo.getLatitude()))
                .setLongitude(String.valueOf(hotelStaticInfo.getLongitude()))
                .setRecommendLevel(0);
        //房型信息
        List<SupplierRoomBaseRequest> roomBaseList = new ArrayList<>();
        List<HotelStaticInfo.Room_groups> roomGroups = hotelStaticInfo.getRoom_groups();
        if (CollectionUtils.isNotEmpty(roomGroups)) {
            roomGroups.forEach(room -> {
                SupplierRoomBaseRequest roomBaseRequest = new SupplierRoomBaseRequest()
                        .setSupplierId(10007)
                        .setSupplierHotelId(hotelId)
                        .setSupplierRoomId(hotelId + "_" + room.getName_struct().getMain_name() + "_" + room.getRg_ext().getBathroom() + "_" + room.getRg_ext().getBedding() + "_" + room.getRg_ext().getCapacity())
                        .setSupplierRoomName(room.getName())
                        .setSupplierRoomNameCN("")
                        .setBroadNet(0)
                        .setBedInfoList(convertBeds(room.getRg_ext().getBedding(), room.getRg_ext().getCapacity()))
                        .setCapacity(2)
                        .setHasBathroom(2 == room.getRg_ext().getBathroom() || 3 == room.getRg_ext().getBathroom() ? 1 : 0)
                        .setHasWindows(0)
                        .setIsSmoking(0);
                roomBaseList.add(roomBaseRequest);
            });
        }
        supplierHotelBaseRequest.setRoomList(roomBaseList);
        return supplierHotelBaseRequest;
    }

    private static List<List<BedInfoDTO>> convertBeds(int bedType, int nums) {
        List<List<BedInfoDTO>> result = Lists.newArrayList();
        List<BedInfoDTO> bedInfoDTOS = Lists.newArrayList();

        BedInfoDTO bedInfoDTO = new BedInfoDTO();
        switch (bedType) {
            case 0:
            case 7:
                bedInfoDTO.setBedType(BedTypeExpediaEnum.OTHER.getValue());
                break;
            case 1:
                bedInfoDTO.setBedType(BedTypeExpediaEnum.BUNK_BED.getValue());
                break;
            case 2:
            case 4:
                bedInfoDTO.setBedType(BedTypeExpediaEnum.TWIN_BED.getValue());
                break;
            case 3:
                bedInfoDTO.setBedType(BedTypeExpediaEnum.FULL_BED.getValue());
                break;
            default:
                bedInfoDTO.setBedType(BedTypeExpediaEnum.OTHER.getValue());
                break;
        }
        bedInfoDTO.setBedNumber(nums);
        bedInfoDTO.setBedDesc("");
        bedInfoDTOS.add(bedInfoDTO);
        result.add(bedInfoDTOS);
        return result;
    }

    public static List<SupplierProductBaseRequest> transformInfoProductReq(QueryProductResponse queryProductResponse) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();
        if (CollectionUtils.isEmpty(queryProductResponse.getHotels())) {
            return list;
        }
        queryProductResponse.getHotels().forEach(hotelPrice -> {
            if (CollectionUtils.isNotEmpty(hotelPrice.getRates())) {
                hotelPrice.getRates().forEach(rate -> {
                    CancellationInfo cancellationPenalties = rate.getPayment_options().getPayment_types().get(0).getCancellation_penalties();
                    SupplierProductBaseRequest request = new SupplierProductBaseRequest()
                            .setSupplierId(10007)
                            .setSupplierHotelId(String.valueOf(hotelPrice.getHid()))
                            .setSupplierRoomId(hotelPrice.getHid() + "_" + rate.getRoom_data_trans().getMain_name() + "_" + rate.getRg_ext().getBathroom() + "_" + rate.getRg_ext().getBedding() + "_" + rate.getRg_ext().getCapacity())
                            .setSupplierProductId(hotelPrice.getHid() + "_" + rate.getRoom_name() + "_" + rate.getMeal() + "_" + (StringUtils.isNotBlank(cancellationPenalties.getFree_cancellation_before()) ? "1" : "0"))
                            .setSupplierProductName(rate.getRoom_name())
                            .setHasWindow(0)
                            .setBreakfast(0)
                            .setCancelType(0);
                    list.add(request);
                });
            }
        });
        return list;
    }

    private static String convertNull(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return str;
    }
}
