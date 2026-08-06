package com.trip.booking.spa.core.api.huitravel.bean.price.check;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckRequest {
    private Integer hid;
    private Integer rid;
    private String rpid;
    private String checkin;
    private String checkout;
    private Integer roomnum;
    private Integer adultnum;
    private String costs;
    private BigDecimal totalprice;
}
