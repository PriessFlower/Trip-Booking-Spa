package com.trip.booking.spa.legacy.huitravel.bean.price.check;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.legacy.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckResponse extends HuiTravelBaseResponse{
    private CheckResult result;
}
