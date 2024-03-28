package com.bingo.hotel.spa.intl.core.exception;

/**
 * @author hz
 */
public class MessageHandleException extends RuntimeException {
    public MessageHandleException() {
    }

    public MessageHandleException(String message) {
        super(message);
    }

    public MessageHandleException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageHandleException(Throwable cause) {
        super(cause);
    }

}
