package com.trip.booking.spa.gateway.adapter.inbound.rest.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@NoArgsConstructor
public class HttpResponse {

    /**
     * 返回的标识
     */
    private int code = HttpStatus.OK.value();

    /**
     * json 封装的对象
     */
    private Object result;

    public HttpResponse(int code) {
        this.code = code;
    }

    public HttpResponse(Object result) {
        this.result = result;
    }

    public HttpResponse(int code, Object result) {
        this.code = code;
        this.result = result;
    }

    public static HttpResponse getSuccessInstance() {
        return new HttpResponse(HttpStatus.OK.value());
    }

    public static HttpResponse getSuccessInstance(Object result) {
        return new HttpResponse(HttpStatus.OK.value(), result);
    }

    public static HttpResponse getInternalErrorInstance() {
        return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public static HttpResponse getInternalErrorInstance(Object result) {
        return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), result);
    }

    public static HttpResponse getNoAuthInstance(Object result) {
        return new HttpResponse(HttpStatus.UNAUTHORIZED.value(), result);
    }
}
