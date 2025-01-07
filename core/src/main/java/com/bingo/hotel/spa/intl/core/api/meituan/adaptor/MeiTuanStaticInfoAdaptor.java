package com.bingo.hotel.spa.intl.core.api.meituan.adaptor;

import com.bingo.hotel.base.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.base.intl.cli.request.RoomBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.HotelInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class MeiTuanStaticInfoAdaptor {

    private static RoomBaseRequest convertBedInfo(Map<String, HotelStaticInfo.BedGroup> bed_groups_us, Map<String, HotelStaticInfo.BedGroup> bed_groups_cn) {
        Set<String> bedTypeSet = new HashSet<>();
        AtomicReference<String> bedNameUS = new AtomicReference<>("");
        AtomicReference<String> bedNameCN = new AtomicReference<>("");
        List<List<BedInfoDTO>> bedInfosList = new ArrayList<>();
        if (null != bed_groups_us && !bed_groups_us.isEmpty()) {
            bed_groups_us.keySet().forEach(bedId -> {
                List<BedInfoDTO> bedInfoDTOS = new ArrayList<>();
                HotelStaticInfo.BedGroup bedGroupUS = bed_groups_us.get(bedId);
                HotelStaticInfo.BedGroup bedGroupCN = bed_groups_cn.get(bedId);
                bedNameUS.set(StringUtils.isBlank(bedNameUS.get()) ? bedGroupUS.getDescription() : bedNameUS + " or " + bedGroupUS.getDescription());
                bedNameCN.set(StringUtils.isBlank(bedNameCN.get()) ? bedGroupCN.getDescription() : bedNameCN + "或" + bedGroupCN.getDescription());
                bedGroupUS.getConfiguration().forEach(bedInfo -> {
                    bedTypeSet.add(bedInfo.getType());
                    BedInfoDTO bedInfoDTO = new BedInfoDTO()
                            .setBedNumber(bedInfo.getQuantity())
                            .setBedDesc(bedInfo.getType())
                            .setBedType(bedInfo.getSize());
                    bedInfoDTOS.add(bedInfoDTO);
                });
                bedInfosList.add(bedInfoDTOS);
            });
        }
        RoomBaseRequest bedInfo = new RoomBaseRequest()
                .setBedType(CollectionUtils.isEmpty(bedTypeSet) ? "" : bedTypeSet.toString())
                .setBedName(bedNameUS.get())
                .setBedNameCN(bedNameCN.get())
                .setBedDesc(JsonUtils.writeObject2Json(bedInfosList));
        return bedInfo;
    }

    public static SupplierHotelBaseRequest transformInfoHotelReq(HotelInfoResponse.HotelInfoResult hotelInfoResult) {
        HotelInfoResponse.BaseInfo baseInfo = hotelInfoResult.getBaseInfo();
        SupplierHotelBaseRequest request = new SupplierHotelBaseRequest()
                .setSupplierId(10009)
                .setSupplierHotelId(baseInfo.getHotelId().toString())
                .setSupplierHotelName(baseInfo.getPointNameEn())
                .setSupplierHotelNameCN(baseInfo.getPointName())
                .setAddress(baseInfo.getAddress())
                .setAddressCN(baseInfo.getAddress())
                .setCountryCode(baseInfo.getCountryName())
                .setCityName(baseInfo.getCityName())
                .setCityNameCN(baseInfo.getCityName())
                .setTelephone(baseInfo.getPhone())
                .setLongitude(new BigDecimal(baseInfo.getLongitude()).divide(new BigDecimal("1000000")).toString())
                .setLatitude(new BigDecimal(baseInfo.getLatitude()).divide(new BigDecimal("1000000")).toString())
                .setRecommendLevel(hotelInfoResult.getExtendInfo().getPoiExtInfo().getStarRating())
                .setScore(baseInfo.getAvgScore().toString());
        return request;
    }

    public static SupplierRoomBaseRequest transformInfoRoomReq(List<RoomInfoResponse.RealRoomInfos> realRoomInfos) {
        //房型信息
//        List<SupplierRoomBaseRequest> roomBaseList = new ArrayList<>();
//        Map<String, HotelStaticInfo.Room> roomUSMap = hotelInfoResult.getRooms();
//        Map<String, HotelStaticInfo.Room> roomCNMap = resultCN.getRooms();
//        if (null != roomUSMap && !roomUSMap.isEmpty()) {
//            roomUSMap.keySet().forEach(roomId -> {
//                HotelStaticInfo.Room roomUS = roomUSMap.get(roomId);
//                HotelStaticInfo.Room roomCN = roomCNMap.get(roomId);
//                SupplierRoomBaseRequest roomBaseRequest = new SupplierRoomBaseRequest()
//                        .setSupplierId(10005)
//                        .setSupplierHotelId(hotelInfoResult.getProperty_id())
//                        .setSupplierRoomId(roomId)
//                        .setSupplierRoomName(roomUS.getName())
//                        .setSupplierRoomNameCN(convertNull(roomCN.getName()))
//                        .setArea(null == roomUS.getArea() ? "0" : String.valueOf(roomUS.getArea().getSquare_meters()))
//                        .setDescription("")
//                        .setBroadNet(0)
//                        .setBedInfoList(new ArrayList<>())
//                        .setCapacity(roomUS.getOccupancy().getMax_allowed().getTotal())
//                        .setHasBathroom(0)
//                        .setHasWindows(0)
//                        .setIsSmoking(0);
//                roomBaseList.add(roomBaseRequest);
//            });
//        }
//        request.setRoomList(roomBaseList);
        return null;
    }

        public static List<SupplierProductBaseRequest> transformInfoProductReq(QueryPriceResponse queryPriceResponse) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();
        if (CollectionUtils.isEmpty(queryPriceResponse.getHotelPrices())) {
            return list;
        }
        queryPriceResponse.getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isNotEmpty(hotelPrice.getRooms())) {
                hotelPrice.getRooms().forEach(roomListBean -> roomListBean.getRates().forEach(rate -> {
                    SupplierProductBaseRequest request = new SupplierProductBaseRequest()
                            .setSupplierId(10005)
                            .setSupplierHotelId(hotelPrice.getProperty_id())
                            .setSupplierRoomId(roomListBean.getId())
                            .setSupplierProductId(rate.getId())
                            .setSupplierProductName(roomListBean.getRoom_name())
                            .setHasWindow(0)
                            .setBreakfast(0)
                            .setCancelType(0);
                    list.add(request);
                }));
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
