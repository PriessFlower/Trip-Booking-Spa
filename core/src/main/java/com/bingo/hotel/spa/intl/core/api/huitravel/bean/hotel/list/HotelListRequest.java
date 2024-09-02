package com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class HotelListRequest {
    private String countrycode;

    private String citycode;
}
