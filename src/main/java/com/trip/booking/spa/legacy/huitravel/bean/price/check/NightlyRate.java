package com.trip.booking.spa.legacy.huitravel.bean.price.check;

import lombok.Data;

@Data
public class NightlyRate {
    private String date;
    private Integer cost;
    private boolean status;
    private Integer roomnum;
}
