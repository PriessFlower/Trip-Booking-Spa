package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.order.create 响应。外层壳同验价；<b>成单判据是 Result.OrderId 非空</b>
 * （移植风险⑧），外层 Code 与订单号同时出现时以订单号为准——响应撕裂时订单已成立，
 * 按错误码判会把已成立的订单误判为失败。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongOrderCreateResponse implements BaseResponse {

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

    /** 外层 Code 里竖线前的错误码（成功时即 "0"） */
    public String errorCode() {
        if (code == null) {
            return null;
        }
        int bar = code.indexOf('|');
        return bar < 0 ? code : code.substring(0, bar);
    }

    /** 供应商订单号；null 即未确证成单 */
    public Long orderId() {
        return result == null ? null : result.getOrderId();
    }

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private Long orderId;

        /** 最迟取消时间 */
        private String cancelTime;

        private BigDecimal guaranteeAmount;

        private Boolean isInstantConfirm;

        /** 支付截止；IsCreateOrderOnly 模式才有意义，本仓下单即扣授信、不走该模式 */
        private String paymentDeadlineTime;
    }
}
