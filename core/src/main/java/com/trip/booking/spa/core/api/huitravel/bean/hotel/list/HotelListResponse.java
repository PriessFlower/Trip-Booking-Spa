package com.trip.booking.spa.core.api.huitravel.bean.hotel.list;

import com.trip.booking.spa.core.api.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class HotelListResponse extends HuiTravelBaseResponse {
    private HotelListResult result;
}
