package com.trip.booking.spa.core.api.didatravel.bean.price.priceConfirm;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import com.trip.booking.spa.core.api.didatravel.bean.price.DidaTravelResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PriceConfirmResponse implements BaseResponse {

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
        private PriceDetailsType PriceDetails;
    }

    @Data
    public static class PriceDetailsType {
        private String ReferenceNo;
        private String CheckInDate;
        private String CheckOutDate;

        private List<HotelType> HotelList;
    }

    @Data
    public static class HotelType {
        private Integer HotelID;
        private String HotelName;
        private HotelTypeDestination Destination;
        private BigDecimal TotalPrice;

        private List<HotelTypeRatePlan> RatePlanList;
        private List<CancellationPolicyListTypeCancellationPolicy> CancellationPolicyList;
        private BigDecimal TotalSalesRate;
        private List<FeeListTypeFeeInfo> IncludedFeeList;
        private List<FeeListTypeFeeInfo> ExcludedFeeList;
    }

    @Data
    public static class HotelTypeDestination {
        private String CityCode;
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
        private List<HotelTypeRatePlanPriceInfo> PriceList;
        private Boolean IsOnRequest;
        private BigDecimal TotalSalesRate;
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
        private Integer SalesRate;
    }

    @Data
    public static class FeeListTypeFeeInfo {
        private String FeeTypeName;
        private String Currency;
        private BigDecimal Amount;
    }

    @Data
    public static class CancellationPolicyListTypeCancellationPolicy {
        private String FromDate;
        private BigDecimal Amount;
        private BigDecimal TotalSalesRate;
    }

}
