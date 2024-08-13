package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import lombok.Data;

@Data
public class NightlyRate {
    private String date;
    private Integer cost;
    private boolean status;
    private Integer roomnum;
}
