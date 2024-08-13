package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import lombok.Data;

import java.util.List;

@Data
public class Price {
    private int hid;
    private int rid;
    private int rpid;
    private String name;
    private String en_name;
    private String checkin;
    private String checkout;
    private int max_occupancy;
    private int breakfast_count;
    private int min_adv_hours;
    private int min_days;
    private int max_days;
    private String cancel_policy;
    private String new_cancel_policy;
    private String national_codes;
    private String national_names;
    private int sale_channel;
    private List<NightlyRate> nightlyrate;
}
