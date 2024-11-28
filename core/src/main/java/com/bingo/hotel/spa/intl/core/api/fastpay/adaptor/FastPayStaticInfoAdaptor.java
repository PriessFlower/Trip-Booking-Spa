package com.bingo.hotel.spa.intl.core.api.fastpay.adaptor;

import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.HotelDetailInfo;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.SearchResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态数据组装.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/21
 * @since : 1.0
 **/
public class FastPayStaticInfoAdaptor {
    public static SupplierHotelBaseRequest transformInfoHotelReq(String supplierHotelId, HotelDetailInfo hotelDetailInfo) {

        SupplierHotelBaseRequest supplierHotelBaseRequest = new SupplierHotelBaseRequest()
                .setSupplierId(10006)
                .setSupplierHotelId(supplierHotelId)
                .setSupplierHotelName(hotelDetailInfo.getName())
                .setSupplierHotelNameCN("")
                .setAddress(hotelDetailInfo.getLocation().getAddress())
                .setAddressCN("")
                .setCountryName(hotelDetailInfo.getLocation().getCountry())
                .setCityName(hotelDetailInfo.getLocation().getCity())
                .setCityNameCN("")
                .setTelephone(hotelDetailInfo.getPhone())
                .setPostcode("")
                .setLatitude(String.valueOf(hotelDetailInfo.getLocation().getLat()))
                .setLongitude(String.valueOf(hotelDetailInfo.getLocation().getLong()))
                .setBrandName(hotelDetailInfo.getChainName())
                .setRecommendLevel(0);
        //房型信息
        List<SupplierRoomBaseRequest> roomBaseList = new ArrayList<>();
        List<HotelDetailInfo.Room> roomList = hotelDetailInfo.getRooms();
        if (CollectionUtils.isNotEmpty(roomList)) {
            roomList.forEach(room -> {
                SupplierRoomBaseRequest roomBaseRequest = new SupplierRoomBaseRequest()
                        .setSupplierId(10006)
                        .setSupplierHotelId(supplierHotelId)
                        .setSupplierRoomId(room.getCode())
                        .setSupplierRoomName(room.getName())
                        .setSupplierRoomNameCN("")
                        .setBroadNet(0)
                        .setBedInfoList(convertBeds(room.getBeds()))
                        .setCapacity(2)
                        .setHasBathroom(0)
                        .setHasWindows(0)
                        .setIsSmoking(0);
                roomBaseList.add(roomBaseRequest);
            });
        }
        supplierHotelBaseRequest.setRoomList(roomBaseList);
        return supplierHotelBaseRequest;
    }

    private static List<List<BedInfoDTO>> convertBeds(List<HotelDetailInfo.DataObject> beds) {
        List<List<BedInfoDTO>> result = Lists.newArrayList();
        List<BedInfoDTO> bedInfoDTOS = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(beds)) {
            beds.forEach(bed -> {
                BedInfoDTO bedInfoDTO = new BedInfoDTO();
                bedInfoDTO.setBedType(bed.getCode());
                bedInfoDTO.setBedNumber(0);
                bedInfoDTO.setBedDesc(bed.getName());
                bedInfoDTOS.add(bedInfoDTO);
            });
        }
        result.add(bedInfoDTOS);
        return result;
    }

    public static List<SupplierProductBaseRequest> transformInfoProductReq(List<SearchResponse.HotelAvail> hotelProductList) {
        List<SupplierProductBaseRequest> supplierProductBaseRequests = Lists.newArrayList();

        for (SearchResponse.HotelAvail hotelAvail : hotelProductList) {
            for (SearchResponse.AvailRoomRate availRoomRate : hotelAvail.getAvailRoomRates()) {
                SupplierProductBaseRequest supplierProductBaseRequest = new SupplierProductBaseRequest()
                        .setSupplierId(10006)
                        .setSupplierHotelId(hotelAvail.getHotelInfo().getCode())
                        .setSupplierRoomId(availRoomRate.getRoomCode())
                        .setSupplierProductId(hotelAvail.getHotelInfo().getCode() + "_" + availRoomRate.getRoomCode() + "_" + availRoomRate.getRatePlanCode())
                        .setSupplierProductName(availRoomRate.getRoomName())
                        .setSupplierProductNameCN("")
                        .setBreakfast(0)
                        .setCancelType(0);
                supplierProductBaseRequests.add(supplierProductBaseRequest);
            }
        }
        return supplierProductBaseRequests;
    }

}
