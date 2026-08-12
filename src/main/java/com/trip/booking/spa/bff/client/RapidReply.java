package com.trip.booking.spa.bff.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次 Rapid 调用的原样结果。body 为解析后的 JsonNode（金额等字符串字段逐字保留，
 * 不经任何数值转换）；解析失败时 body 为 null，raw 始终保留原文。
 */
public class RapidReply {

    private final int status;
    private final JsonNode body;
    private final String raw;
    /** 网络层异常（超时、连接失败等）；此时 status=0 */
    private final Exception transportError;

    public RapidReply(int status, JsonNode body, String raw, Exception transportError) {
        this.status = status;
        this.body = body;
        this.raw = raw;
        this.transportError = transportError;
    }

    public int getStatus() {
        return status;
    }

    public JsonNode getBody() {
        return body;
    }

    public String getRaw() {
        return raw;
    }

    public Exception getTransportError() {
        return transportError;
    }

    public boolean is2xx() {
        return status >= 200 && status < 300;
    }

    /** 结果不确定：网络异常、无状态码、429/499/5xx——供应商可能已受理 */
    public boolean isIndeterminate() {
        return transportError != null || status == 0 || status == 429 || status == 499 || status >= 500;
    }
}
