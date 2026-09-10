package com.trip.booking.spa.b2b.web;

/** b2b 层业务异常：message 直接面向代理商展示 */
public class B2bException extends RuntimeException {

    private final int httpStatus;

    public B2bException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
