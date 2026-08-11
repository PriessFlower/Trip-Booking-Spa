package com.trip.booking.spa.bff.web;

/** bff 层业务异常：message 直接面向前端展示 */
public class BffException extends RuntimeException {

    private final int httpStatus;

    public BffException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
