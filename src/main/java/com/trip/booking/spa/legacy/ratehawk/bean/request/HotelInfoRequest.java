package com.trip.booking.spa.legacy.ratehawk.bean.request;

import lombok.Builder;

/**
 * 酒店信息入参
 * HotelInfoRequest
 */

@Builder
public class HotelInfoRequest {

    private String inventory;
    private String language;

    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public String getInventory() {
        return inventory;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

}

