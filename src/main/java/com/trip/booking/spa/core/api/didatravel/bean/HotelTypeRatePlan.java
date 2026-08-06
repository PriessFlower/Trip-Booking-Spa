package com.trip.booking.spa.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 酒店产品信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Accessors(chain = true)
public class HotelTypeRatePlan {
    private Integer TotalPrice;
    private Integer RoomStatus;
    private Integer BreakfastType;
    private Integer BedType;
    private Integer StandardOccupancy;
    private Integer InventoryCount;
    private Integer MaxOccupancy;
    private Integer RoomTypeID;
    private Integer RecommendIndex;
    private String Currency;
    private String RatePlanName;
    private String RatePlanID;
    private String RoomName_CN;
    private String RoomName;
    private boolean IsOnRequest;
    private RoomOccupancyType RoomOccupancy;
    private List<HotelTypeRatePlanPriceInfo> PriceList;
    private List<CancellationPolicyListTypeCancellationPolicy> RatePlanCancellationPolicyList;
    private List<FeeListTypeFeeInfo> IncludedFeeList;
    private List<FeeListTypeFeeInfo> ExcludedFeeList;
}
