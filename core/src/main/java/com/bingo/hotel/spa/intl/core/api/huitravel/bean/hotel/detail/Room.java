package com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Room {
    private int rid;
    private String name;
    private String en_name;
    private String internet;
    private int max_occupancy;
    private String area;
    private String floor;
    private String bed_type;
    private String bed_size;
    private int window_type;
    private int cutoff_hour;
}
