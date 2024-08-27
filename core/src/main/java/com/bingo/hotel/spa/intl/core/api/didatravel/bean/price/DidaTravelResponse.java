package com.bingo.hotel.spa.intl.core.api.didatravel.bean.price;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Data;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class DidaTravelResponse implements BaseResponse {

    private Success Success;
    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    @Data
    public static class Success {
        private PriceDetails PriceDetails;
    }

    @Data
    public static class PriceDetails {
        private Date CheckInDate;
        private Date CheckOutDate;

        private List<HotelType> HotelList;
    }

    @Data
    public static class HotelTypeDestinationBean {
        private String CityCode;
    }

    @Data
    public static class HotelTypeLowestPrice {
        private String Currency;
        private String RatePlanID;
        private BigDecimal value;
    }

    @Data
    public static class HotelTypeRatePlan {
        private RoomOccupancyType RoomOccupancy;

        private Integer RoomTypeID;

        private String RoomName;

        private String RoomName_CN;

        private String RatePlanID;

        private Integer RecommendIndex;

        private String RatePlanName;

        private Integer BedType;

        private Integer BreakfastType;

        private Integer InventoryCount;

        private String Currency;

        private BigDecimal TotalPrice;

        private Integer maxOccupancy;

        private List<HotelTypeRatePlanPriceInfo> PriceList;

        private List<CancellationPolicyListTypeCancellationPolicy> RatePlanCancellationPolicyList;

        private Boolean IsOnRequest;

        private List<FeeListTypeFeeInfo> IncludedFeeList;

        private List<FeeListTypeFeeInfo> ExcludedFeeList;
    }

    @Data
    public static class RoomOccupancyType {
        private List<Integer> ChildAgeDetails;

        private Integer RoomNum;

        private Integer AdultCount;

        private Integer ChildCount;
    }

    @Data
    public static class HotelTypeRatePlanPriceInfo {
        private BigDecimal Price;

        private Integer InventoryCount;

        private String StayDate;

        private Integer MealType;

        private Integer MealAmount;
    }

    @Data
    public static class CancellationPolicyListTypeCancellationPolicy {
        private Date FromDate;

        private BigDecimal Amount;
    }

    @Data
    public static class FeeListTypeFeeInfo {
        private String FeeTypeName;

        private String Currency;

        private BigDecimal Amount;
    }

    @Data
    public static class HotelTypeLowestRateRatePlanInfo {
        private String FormDate;
        private BigDecimal Amount;
    }

    @Data
    public static class HotelType {
        private Integer HotelID;
        private String HotelName;


        private HotelTypeDestinationBean Destination;

        private BigDecimal TotalPrice;

        private HotelTypeLowestPrice LowestPrice;

        private List<HotelTypeRatePlan> RatePlanList;

        private HotelTypeLowestRateRatePlanInfo LowestRateRatePlanInfo;

    }
}