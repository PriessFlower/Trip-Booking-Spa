package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * HotelBookingCancelConfirm（确认取消）响应（官方 booking-api/booking-cancel/booking-cancel-confirm，
 * 2026-09-13 查阅）：成功是<b>空对象</b> {@code {"Success":{}}}（cursor 生产账号 2026-01-31 实测同形），
 * 失败是 {@code Error}。成功节点不带任何字段，故"取消到底生效没有"还要再查单看 Status 是否为 3
 * ——官方 booking-search：「Status=3 表示订单已在 Dida 系统中成功取消，这是该状态的唯一有效确认方式」。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaBookingCancelConfirmResponse implements BaseResponse {

    @JsonProperty("Success")
    private Success success;

    @JsonProperty("Error")
    private DidaError error;

    @Override
    public boolean isSucc() {
        return error == null && success != null;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public String errorCode() {
        return error == null ? null : error.getCode();
    }

    public String errorMessage() {
        return error == null ? null : error.getMessage();
    }

    /** 空对象：官方字段表只有 "Success: Object"，无子字段 */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Success {
    }
}
