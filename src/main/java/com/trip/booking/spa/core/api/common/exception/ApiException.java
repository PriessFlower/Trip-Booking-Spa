package com.trip.booking.spa.core.api.common.exception;

public class ApiException extends Exception {

    private static final long serialVersionUID = -1043112048302549645L;

    public ApiException() {
        super();
    }

    public ApiException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public ApiException(String arg0) {
        super(arg0);
    }

    public ApiException(Throwable arg0) {
        super(arg0);
    }

}
