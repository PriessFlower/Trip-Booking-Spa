package com.trip.booking.spa.core.api.huitravel.bean.price.availability;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailabilityRequest {
    private String hid;
    private Integer rid;
    private String rpid;
    private String checkin;
    private String checkout;
    private Integer roomnum;
    private Integer adultnum;
    private String nationality;
}
