package com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Room {
    private Integer rid;
    private String name;
    private String en_name;
    private String internet;
    private Integer max_occupancy;
    private String area;
    private String floor;
    private String bed_type;
    private String bed_size;
    private Integer window_type;
    private Integer cutoff_hour;
}
