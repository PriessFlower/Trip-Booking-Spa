package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability;

import lombok.Data;

import java.util.List;

@Data
public class Price {
    private Integer hid;
    private Integer rid;
    private String rpid;
    private String name;
    private String en_name;
    private String room_name;
    private String room_en_name;
    private String checkin;
    private String checkout;
    private Integer max_occupancy;
    private Integer breakfast_count;
    private Integer min_adv_hours;
    private Integer min_days;
    private Integer max_days;
    private String cancel_policy;
    private String new_cancel_policy;
    private String national_codes;
    private String national_names;
    private Integer sale_channel;
    private List<NightlyRate> nightlyrate;
}
