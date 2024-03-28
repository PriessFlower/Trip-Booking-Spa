package com.bingo.hotel.spa.intl.core.exception;

public class RedisLimitException extends RuntimeException {
    public RedisLimitException(String msg) {
        super(msg);
    }
}
