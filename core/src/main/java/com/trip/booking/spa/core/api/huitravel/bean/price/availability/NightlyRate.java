package com.trip.booking.spa.core.api.huitravel.bean.price.availability;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class NightlyRate {
    private String date;
    private BigDecimal cost;
    private boolean status;
    private Integer roomnum;
}
