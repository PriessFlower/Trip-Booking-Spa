package com.trip.booking.spa.core.api.huitravel.bean.price.availability;

import com.trip.booking.spa.core.api.huitravel.bean.HuiTravelBaseResponse;
import com.trip.booking.spa.core.api.huitravel.bean.price.check.CheckResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityResponse extends HuiTravelBaseResponse {
    private  AvailabilityResult result;
    private CheckResponse checkResponse;
}
