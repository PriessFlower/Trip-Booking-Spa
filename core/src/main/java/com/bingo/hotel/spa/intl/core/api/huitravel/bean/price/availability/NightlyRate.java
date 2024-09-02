package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class NightlyRate {
    private String date;
    private BigDecimal cost;
    private boolean status;
    private Integer roomnum;
}
