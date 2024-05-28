package com.bingo.hotel.spa.intl.core.api.didatravel.bean.price;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Builder;
import org.joda.time.DateTime;

import java.util.List;

@Builder
public class DidaTravelRequest {
    @JSONField(name = "Header")
    private HeaderType Header;

    @JSONField(name = "Destination")
    private PriceSearchRequestDestination Destination;

    @JSONField(name = "HotelIDList")
    private List<Integer> HotelIDList;

    @JSONField(name = "CheckInDate")
    private String CheckInDate;

    @JSONField(name = "CheckOutDate")
    private String CheckOutDate;

    @JSONField(name = "Nationality")
    private String Nationality;

    @JSONField(name = "LowestPriceOnly")
    private Boolean LowestPriceOnly;

    @JSONField(name = "IsRealTime")
    private PriceSearchRequestIsRealTime IsRealTime;

    @JSONField(name = "RealTimeOccupancy")
    private PriceSearchRequestRealTimeOccupancy RealTimeOccupancy;

    @JSONField(name = "Currency")
    private String Currency;

    @JSONField(name = "IsNeedOnRequest")
    private Boolean IsNeedOnRequest;

    public static class HeaderType {
        @JSONField(name = "ClientID")
        private String ClientID;

        @JSONField(name = "LicenseKey")
        private String LicenseKey;

        public String getClientID() {
            return ClientID;
        }

        public void setClientID(String clientID) {
            ClientID = clientID;
        }

        public String getLicenseKey() {
            return LicenseKey;
        }

        public void setLicenseKey(String licenseKey) {
            LicenseKey = licenseKey;
        }
    }

    public static class PriceSearchRequestDestination {
        @JSONField(name = "CityCode")
        private String CityCode;

        public String getCityCode() {
            return CityCode;
        }

        public void setCityCode(String cityCode) {
            CityCode = cityCode;
        }
    }

    public static class PriceSearchRequestIsRealTime {
        @JSONField(name = "RoomCount")
        private Integer RoomCount;

        @JSONField(name = "Value")
        private Boolean Value;

        public Integer getRoomCount() {
            return RoomCount;
        }

        public void setRoomCount(Integer roomCount) {
            RoomCount = roomCount;
        }

        public Boolean getValue() {
            return Value;
        }

        public void setValue(Boolean value) {
            Value = value;
        }
    }

    public static class PriceSearchRequestRealTimeOccupancy {
        @JSONField(name = "ChildAgeDetails")
        private List<Integer> ChildAgeDetails;

        @JSONField(name = "AdultCount")
        private Integer AdultCount;

        @JSONField(name = "ChildCount")
        private Integer ChildCount;

        public List<Integer> getChildAgeDetails() {
            return ChildAgeDetails;
        }

        public void setChildAgeDetails(List<Integer> childAgeDetails) {
            ChildAgeDetails = childAgeDetails;
        }

        public Integer getAdultCount() {
            return AdultCount;
        }

        public void setAdultCount(Integer adultCount) {
            AdultCount = adultCount;
        }

        public Integer getChildCount() {
            return ChildCount;
        }

        public void setChildCount(Integer childCount) {
            ChildCount = childCount;
        }
    }

    public HeaderType getHeader() {
        return Header;
    }

    public void setHeader(HeaderType header) {
        Header = header;
    }

    public PriceSearchRequestDestination getDestination() {
        return Destination;
    }

    public void setDestination(PriceSearchRequestDestination destination) {
        Destination = destination;
    }

    public List<Integer> getHotelIDList() {
        return HotelIDList;
    }

    public void setHotelIDList(List<Integer> hotelIDList) {
        HotelIDList = hotelIDList;
    }

    public String getCheckInDate() {
        return CheckInDate;
    }

    public void setCheckInDate(String checkInDate) {
        CheckInDate = checkInDate;
    }

    public String getCheckOutDate() {
        return CheckOutDate;
    }

    public void setCheckOutDate(String checkOutDate) {
        CheckOutDate = checkOutDate;
    }

    public String getNationality() {
        return Nationality;
    }

    public void setNationality(String nationality) {
        Nationality = nationality;
    }

    public Boolean getLowestPriceOnly() {
        return LowestPriceOnly;
    }

    public void setLowestPriceOnly(Boolean lowestPriceOnly) {
        LowestPriceOnly = lowestPriceOnly;
    }

    public PriceSearchRequestIsRealTime getIsRealTime() {
        return IsRealTime;
    }

    public void setIsRealTime(PriceSearchRequestIsRealTime isRealTime) {
        IsRealTime = isRealTime;
    }

    public PriceSearchRequestRealTimeOccupancy getRealTimeOccupancy() {
        return RealTimeOccupancy;
    }

    public void setRealTimeOccupancy(PriceSearchRequestRealTimeOccupancy realTimeOccupancy) {
        RealTimeOccupancy = realTimeOccupancy;
    }

    public String getCurrency() {
        return Currency;
    }

    public void setCurrency(String currency) {
        Currency = currency;
    }

    public Boolean getNeedOnRequest() {
        return IsNeedOnRequest;
    }

    public void setNeedOnRequest(Boolean needOnRequest) {
        IsNeedOnRequest = needOnRequest;
    }
}
