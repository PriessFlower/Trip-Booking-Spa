package com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class HotelDetail {
    private int hid;
    private String name;
    private String en_name;
    private String tel;
    private String address;
    private String domestic;
    private int country_code;
    private int province_code;
    private int city_code;
    private String country;
    private String en_country;
    private String province;
    private String city;
    private String district;
    private String business;
    private String longitude;
    private String latitude;
    private String position_type;
    private String star;
    private String surroundings;
    private String main_imgs;
    private String description;
    private String order_index;
    private int reunion_room_min_count;
    private List<Room> rooms;
}
