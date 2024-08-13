package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CheckRequest {
    private Integer hid;
    private Integer rid;
    private Integer rpid;
    private String checkin;
    private String checkout;
    private Integer roomnum;
    private Integer adultnum;
    private String costs;
    private BigDecimal totalprice;
}
