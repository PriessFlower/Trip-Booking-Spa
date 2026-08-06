package com.trip.booking.spa.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class HotelInfo {

    private long id;
    private String nameCN;
    private String cityCode;
    private String group;

}
