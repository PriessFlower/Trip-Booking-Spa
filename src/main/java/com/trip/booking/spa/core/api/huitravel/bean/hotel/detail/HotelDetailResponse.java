package com.trip.booking.spa.core.api.huitravel.bean.hotel.detail;

import com.trip.booking.spa.core.api.huitravel.bean.HuiTravelBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelDetailResponse extends HuiTravelBaseResponse {
    private HotelDetailResult result;
}
