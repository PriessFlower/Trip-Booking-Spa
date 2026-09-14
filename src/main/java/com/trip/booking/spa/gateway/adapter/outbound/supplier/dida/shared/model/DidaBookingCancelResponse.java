package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * HotelBookingCancel（预取消）响应（官方 booking-api/booking-cancel/booking-pre-cancel，
 * 2026-09-13 查阅）：{@code Success{BookingID, ConfirmID, Currency, Amount}} 或 {@code Error}。
 * {@code ConfirmID}「有效期 10 分钟，这个值需要在下一步确认取消中传回」；{@code Amount} 是取消罚金，
 * 与 {@code Currency} 配对——这是道旅唯一给出罚金的地方（确认取消的响应是空对象）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaBookingCancelResponse implements BaseResponse {

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

    public String confirmId() {
        return success == null ? null : success.getConfirmId();
    }

    public String bookingId() {
        return success == null ? null : success.getBookingId();
    }

    public BigDecimal amount() {
        return success == null ? null : success.getAmount();
    }

    public String currency() {
        return success == null ? null : success.getCurrency();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Success {

        @JsonProperty("BookingID")
        private String bookingId;

        @JsonProperty("ConfirmID")
        private String confirmId;

        @JsonProperty("Currency")
        private String currency;

        /** 取消罚金（大单位） */
        @JsonProperty("Amount")
        private BigDecimal amount;
    }
}
