package com.bingo.hotel.spa.intl.core.api.meituan.bean.response;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HotelIdsResult {

    private long maxId;
    private int partnerId;
    private List<Integer> hotelIds;

}
