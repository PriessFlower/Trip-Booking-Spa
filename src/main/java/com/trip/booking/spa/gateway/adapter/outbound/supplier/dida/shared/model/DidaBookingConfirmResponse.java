package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * HotelBookingConfirm（下单）响应（官方 booking-api/booking-confirm，2026-09-13 查阅）：
 * {@code Success.BookingDetails} 或 {@code Error{Code,Message,BookingID}}。
 *
 * <p>Error 节点带 {@code BookingID} 是下单族独有的形态（查价族没有）：错误响应里出现单号，
 * 意味着道旅侧已有一笔与本请求相关的订单——分类时它优先于错误码（见 DidaBookingClassifier）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaBookingConfirmResponse implements BaseResponse {

    @JsonProperty("Success")
    private Success success;

    @JsonProperty("Error")
    private DidaError error;

    @Override
    public boolean isSucc() {
        return error == null && success != null;
    }

    /** 订单族没有「空结果」这一档：成功必带 BookingDetails，缺了是契约撕裂，由分类器判不确定 */
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

    public DidaBookingDetails bookingDetails() {
        return success == null ? null : success.getBookingDetails();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Success {

        @JsonProperty("BookingDetails")
        private DidaBookingDetails bookingDetails;
    }
}
