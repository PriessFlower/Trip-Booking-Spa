package com.bingo.hotel.spa.intl.core.api.meituan.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;

/***
 * @author Hjh
 * @website
 */
@Setter
@Getter
public class HotelInfoResponse implements BaseResponse {

    private Integer code;
    private String message;
    private Integer partnerId;
    private List<HotelInfoResult> result;

    @Override
    public boolean isSucc() {
        return code == 0;
    }

    @Override
    public boolean isEmptyResult() {
        return result == null || CollectionUtils.isEmpty(result);
    }

    @Setter
    @Getter
    public static class HotelInfoResult {

        private Long hotelId;
        private BaseInfo baseInfo;
        private List<Image> poiImages;
        private ExtendInfo extendInfo;
    }

    @Setter
    @Getter
    public static class ExtendInfo {

        private Map<Integer, String> hotelService;
        private PoiExtInfo poiExtInfo;
        private Map<Integer, String> hotelFacilities;
    }

    @Getter
    @Setter
    public static class PoiExtInfo {

        private String checkinTimeEnd;
        private String checkinTimeBegin;
        private Long hotelId;
        private String openDate;
        private String decorationDate;
        private Integer starRating;
        private String checkoutTime;

    }

    @Setter
    @Getter
    public static class BaseInfo {

        private Integer closeStatus;
        private String address;
        private String cityName;
        private Integer avgScore;
        private String frontImage;
        private String phone;
        private String pointName;
        private Long latitude;
        private Long hotelId;
        private String pointNameEn;
        private String countryName;
        private Long longitude;
    }

    @Setter
    @Getter
    public static class Image {

        private String typeName;
        private Integer typeId;
        private String url;
        private String imgDesc;
    }

}