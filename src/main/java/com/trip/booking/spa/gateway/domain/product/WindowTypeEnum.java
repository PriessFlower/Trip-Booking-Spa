package com.trip.booking.spa.gateway.domain.product;

import lombok.Getter;

@Getter
public enum WindowTypeEnum {

    NO("0"),            //无窗
    AVAILABLE("1"),     //有窗
    PARTIALLY("2");     //部分有窗


    private final String code;

    WindowTypeEnum(String code) {
        this.code = code;
    }

}
