package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 道旅的错误信封 {@code {"Error":{"Code":"2017","Message":"Invalid LicenseKey"}}}。
 * 码表见官方 {@code information-hub/api-error-code}（2026-09-08 查阅），分态映射在
 * {@code DidaOutcomes}。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaError {

    @JsonProperty("Code")
    private String code;

    @JsonProperty("Message")
    private String message;

    /** 会话 id，仅 PriceConfirm 的错误分支下发，报障时供应商据此定位 */
    @JsonProperty("SessionID")
    private String sessionId;

    /**
     * 道旅订单号，仅订单族接口（booking-confirm/search/cancel）的错误分支可能下发
     * （官方各页 Error 字段表，2026-09-13 查阅）。错误响应里带单号=道旅侧已有相关订单，
     * 下单分类时它优先于错误码。
     */
    @JsonProperty("BookingID")
    private String bookingId;
}
