package com.trip.booking.spa.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 酒店最低价产品信息.
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
public class HotelTypeLowestRateRatePlanInfo {
    private Integer Breakfast;
    private Integer BedType;
    private String RatePlanName;
    private String RatePlanID;
    private String RoomTypeID;
    private List<CancellationPolicyListTypeCancellationPolicy> RatePlanCancellationPolicyList;
}
