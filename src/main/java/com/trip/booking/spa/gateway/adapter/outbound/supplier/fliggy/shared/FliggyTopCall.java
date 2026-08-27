package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 TOP 调用的载体：method + 业务参数（对象型参数以 JSON 串为值，TOP 惯例）。
 * 公共参数（app_key/session/timestamp/sign 等）由 {@link AbstractFliggyTopAccess} 统一装配。
 */
public class FliggyTopCall {

    private final String method;
    private final Map<String, String> bizParams;

    public FliggyTopCall(String method, Map<String, String> bizParams) {
        this.method = method;
        this.bizParams = new LinkedHashMap<>(bizParams);
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getBizParams() {
        return bizParams;
    }

    @Override
    public String toString() {
        return method + bizParams;
    }
}
