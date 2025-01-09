package com.bingo.hotel.spa.intl.core.api.meituan.adaptor;

import com.bingo.hotel.base.intl.cli.enums.BedTypeExpediaEnum;
import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.HotelInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.ProductInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.RoomInfoResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MeiTuanStaticInfoAdaptor {

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

    public static List<SupplierRoomBaseRequest> transformInfoRoomReq(List<RoomInfoResponse.RealRoomInfos> realRoomInfos, Long hotelId) {

        //房型信息
        List<SupplierRoomBaseRequest> roomBaseList = new ArrayList<>();

        if (CollectionUtils.isEmpty(realRoomInfos)) {
            return roomBaseList;
        }
        realRoomInfos.forEach(roomInfo -> {
            RoomInfoResponse.RealRoomBaseInfo realRoomBaseInfo = roomInfo.getRealRoomBaseInfo();
            SupplierRoomBaseRequest roomBaseRequest = new SupplierRoomBaseRequest()
                    .setSupplierId(10009)
                    .setSupplierHotelId(hotelId.toString())
                    .setSupplierRoomId(realRoomBaseInfo.getRealRoomId().toString())
                    .setSupplierRoomName(realRoomBaseInfo.getRoomNameEn())
                    .setSupplierRoomNameCN(realRoomBaseInfo.getRoomName())
                    .setArea(realRoomBaseInfo.getUseableArea())
                    .setDescription("")
                    .setFloor(realRoomBaseInfo.getFloor())
                    .setBroadNet(convertInternet(realRoomBaseInfo.getInternetWay()))
                    .setBedInfoList(convertBedList(roomInfo.getBedInfoList()))
                    .setHasBathroom(0)
                    .setHasWindows(convertWindows(realRoomBaseInfo.getWindow()))
                    .setIsSmoking(0);
            roomBaseList.add(roomBaseRequest);
        });
        return roomBaseList;
    }

    private static Integer convertInternet(Integer internet) {
        switch (internet) {
            case 0:
                return 0;
            case 1:
                return 4;
            case 2:
                return 2;
            case 3:
                return 4;
            case 4:
                return 3;
            case 5:
                return 4;
            default:
                return 0;
        }
    }

    private static Integer convertWindows(Integer windows) {
        switch (windows) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 0;
            default:
                return 0;
        }
    }

    private static List<List<BedInfoDTO>> convertBedList(List<List<RoomInfoResponse.BedInfoList>> bedInfoList) {
        List<List<BedInfoDTO>> bedInfosList = new ArrayList<>();
        if (CollectionUtils.isEmpty(bedInfoList)) {
            return bedInfosList;
        }
        bedInfoList.forEach(bedInfos -> {
            List<BedInfoDTO> bedInfoDTOS = new ArrayList<>();
            bedInfos.forEach(bedInfo -> {
                BedInfoDTO bedInfoDTO = new BedInfoDTO()
                        .setBedType(convertBedType(bedInfo.getBedType()))
                        .setBedNumber(bedInfo.getBedCount())
                        .setBedDesc(bedInfo.getBedDesc());
                bedInfoDTOS.add(bedInfoDTO);
            });
        });
        return bedInfosList;
    }

    private static String convertBedType(String bedType) {
        switch (bedType) {
            case "单人床":
                return BedTypeExpediaEnum.TWIN_BED.getValue() + "";
            case "大床":
                return BedTypeExpediaEnum.QUEEN_BED.getValue() + "";
            case "特大床":
                return BedTypeExpediaEnum.KING_BED.getValue() + "";
            case "小型双人床":
            case "双人床":
                return BedTypeExpediaEnum.FULL_BED.getValue() + "";
            case "沙发床":
                return BedTypeExpediaEnum.SOFA_BED.getValue() + "";
            case "双层床":
            case "上下铺":
                return BedTypeExpediaEnum.BUNK_BED.getValue() + "";
            case "水床":
                return BedTypeExpediaEnum.WATER_BED.getValue() + "";
            case "日式床":
            case "榻榻米":
                return BedTypeExpediaEnum.FUTON.getValue() + "";
            case "子母床":
            case "壁柜床":
                return BedTypeExpediaEnum.TRUNDLE_BED.getValue() + "";
            default:
                return BedTypeExpediaEnum.OTHER.getValue() + "";
        }

    }


    public static List<SupplierProductBaseRequest> transformInfoProductReq(List<ProductInfoResponse.Result> productInfoResult) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();
        if (CollectionUtils.isEmpty(productInfoResult)) {
            return list;
        }
        productInfoResult.forEach(hotelInfo -> {
            hotelInfo.getGoodsList().forEach(productInfo->{
                SupplierProductBaseRequest request = new SupplierProductBaseRequest()
                        .setSupplierId(10009)
                        .setSupplierHotelId(hotelInfo.getHotelId().toString())
                        .setSupplierRoomId(productInfo.getRealRoomId().toString())
                        .setSupplierProductId(productInfo.getGoodsId().toString())
                        .setSupplierProductName(productInfo.getGoodsName())
                        .setSupplierProductNameCN(productInfo.getGoodsName())
                        .setHasWindow(0)
                        .setBreakfast(productInfo.getMealType().getCount())
                        .setCancelType(1 == productInfo.getRefundable() ? 0 : 1);
                list.add(request);
            });
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
