package com.trip.booking.spa.legacy.huitravel.bean.price.availability;

import com.trip.booking.spa.legacy.huitravel.bean.HuiTravelBaseResponse;
import com.trip.booking.spa.legacy.huitravel.bean.price.check.CheckResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityResponse extends HuiTravelBaseResponse {
    private  AvailabilityResult result;
    private CheckResponse checkResponse;
}
