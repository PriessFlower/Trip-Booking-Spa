package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.HuiTravelBaseResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityResponse extends HuiTravelBaseResponse {
    private  AvailabilityResult result;
    private CheckResponse checkResponse;
}
