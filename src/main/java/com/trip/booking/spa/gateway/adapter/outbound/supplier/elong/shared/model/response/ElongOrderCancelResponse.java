package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.order.cancel 响应。成功字段拼写 {@code Successs}（三个 s）为艺龙官方原文
 * （移植风险⑨，官方文档 2026-08-15 核对确认），显式 @JsonProperty 钉死，
 * 禁止"纠正"拼写——纠了就永远解析不到。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongOrderCancelResponse implements BaseResponse {

    private String code;

    private String guid;

    private Result result;

    @Override
    public boolean isSucc() {
        return "0".equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return result == null;
    }

    public String errorCode() {
        if (code == null) {
            return null;
        }
        int bar = code.indexOf('|');
        return bar < 0 ? code : code.substring(0, bar);
    }

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        /** 艺龙原文拼写，三个 s；true=取消请求已受理 */
        @JsonProperty("Successs")
        private Boolean successs;

        /** 本次取消产生的违约金（元，v1.32+） */
        private BigDecimal penaltyAmount;
    }
}
