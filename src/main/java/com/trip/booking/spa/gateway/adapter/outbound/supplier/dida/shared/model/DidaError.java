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
}
