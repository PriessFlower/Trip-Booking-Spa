package com.trip.booking.spa.rest.exception;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum HotelBaseServiceErrorCode {

    SYSTEM_EXCEPTION(10000, "system error");

    public final int code;
    public final String msg;
}
