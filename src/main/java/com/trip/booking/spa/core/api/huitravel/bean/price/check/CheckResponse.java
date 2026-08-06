package com.trip.booking.spa.core.api.huitravel.bean.price.check;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import com.trip.booking.spa.core.api.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckResponse extends HuiTravelBaseResponse{
    private CheckResult result;
}
