package com.trip.booking.spa.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店信息集合.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class HotelType {
    private Integer HotelID;
    private String HotelName;
    private BigDecimal TotalPrice;
    private HotelTypeDestination Destination;
    private HotelTypeLowestPrice LowestPrice;
    private List<HotelTypeRatePlan> RatePlanList;
    private HotelTypeLowestRateRatePlanInfo LowestRateRatePlanInfo;
}
