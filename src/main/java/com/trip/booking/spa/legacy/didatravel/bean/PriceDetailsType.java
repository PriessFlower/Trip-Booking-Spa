package com.trip.booking.spa.legacy.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 报价相关信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class PriceDetailsType {

    private String CheckOutDate;
    private String CheckInDate;
    private List<HotelType> HotelList;
}
