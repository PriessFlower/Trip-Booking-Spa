package com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Location {

    private int locationtype;
    private double longitude;
    private double latitude;
    private double distance;
}