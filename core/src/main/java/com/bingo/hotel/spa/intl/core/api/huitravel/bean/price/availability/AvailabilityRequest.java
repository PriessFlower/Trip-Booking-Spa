package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AvailabilityRequest {
    private Integer hid;
    private Integer rid;
    private Integer rpid;
    private String checkin;
    private String checkout;
    private Integer roomnum;
    private Integer adultnum;
    private String nationality;
}
