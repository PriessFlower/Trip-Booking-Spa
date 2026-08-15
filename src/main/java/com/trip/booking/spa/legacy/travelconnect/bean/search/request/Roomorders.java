package com.trip.booking.spa.legacy.travelconnect.bean.search.request;

import lombok.Builder;

import java.util.List;

@Builder
public class Roomorders {
    /**
     * adultcount : 1
     * infantcount : 2
     * childagelist : [1,2]
     */

    private int adultcount;
    private int infantcount;
    private List<Integer> childagelist;

    public int getAdultcount() {
        return adultcount;
    }

    public void setAdultcount(int adultcount) {
        this.adultcount = adultcount;
    }

    public int getInfantcount() {
        return infantcount;
    }

    public void setInfantcount(int infantcount) {
        this.infantcount = infantcount;
    }

    public List<Integer> getChildagelist() {
        return childagelist;
    }

    public void setChildagelist(List<Integer> childagelist) {
        this.childagelist = childagelist;
    }
}