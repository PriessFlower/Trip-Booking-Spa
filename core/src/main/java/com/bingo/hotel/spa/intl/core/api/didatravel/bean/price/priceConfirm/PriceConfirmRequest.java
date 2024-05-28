package com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm;

import com.alibaba.fastjson.annotation.JSONField;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class PriceConfirmRequest {
    @JSONField(name = "Header")
    private HeaderType Header;
    @JSONField(name = "HotelID")
    private Integer HotelID;
    @JSONField(name = "RatePlanID")
    private String RatePlanID;
    @JSONField(name = "CheckInDate")
    private String CheckInDate;
    @JSONField(name = "CheckOutDate")
    private String CheckOutDate;
    @JSONField(name = "Nationality")
    private String Nationality;;
    @JSONField(name = "NumOfRooms")
    private Integer NumOfRooms;
    @JSONField(name = "OccupancyDetails")
    private List<RoomOccupancyType> OccupancyDetails;
    @JSONField(name = "PreBook")
    private Boolean PreBook;
    @JSONField(name = "Currency")
    private String Currency;
    @JSONField(name = "IsNeedOnRequest")
    private Boolean IsNeedOnRequest;

    @Data
    public static class HeaderType {
        @JSONField(name = "ClientID")
        private String ClientID;
        @JSONField(name = "LicenseKey")
        private String LicenseKey;
    }
    @Data
    public static class RoomOccupancyType {
        @JSONField(name = "ChildAgeDetails")
        private List<Integer> ChildAgeDetails;
        @JSONField(name = "RoomNum")
        private Integer RoomNum;
        @JSONField(name = "AdultCount")
        private Integer AdultCount;
        @JSONField(name = "ChildCount")
        private Integer ChildCount;
    }

}
