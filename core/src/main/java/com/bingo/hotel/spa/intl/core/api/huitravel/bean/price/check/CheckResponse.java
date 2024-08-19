package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckResponse extends HuiTravelBaseResponse {
    private CheckResult result;
}
