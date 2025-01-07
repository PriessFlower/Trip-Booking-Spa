package com.bingo.hotel.spa.intl.core.api.meituan.bean.request;

import lombok.Builder;

import java.util.List;


@Builder
public class HotelInfoReqBody {


    private List<Long> hotelIds;
    private Integer strategy;

    public HotelInfoReqBody(List<Long> hotelIds, Integer strategy) {
        this.hotelIds = hotelIds;
        this.strategy = strategy;
    }

    public List<Long> getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(List<Long> hotelIds) {
        this.hotelIds = hotelIds;
    }

    public Integer getStrategy() {
        return strategy;
    }

    public void setStrategy(Integer strategy) {
        this.strategy = strategy;
    }
}
