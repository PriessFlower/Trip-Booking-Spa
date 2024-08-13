package com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class HotelListResponse extends HuiTravelBaseResponse {
    private HotelListResult result;
}
