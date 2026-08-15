package com.trip.booking.spa.platform.exception;

public class RedisLimitException extends RuntimeException {
    public RedisLimitException(String msg) {
        super(msg);
    }
}
