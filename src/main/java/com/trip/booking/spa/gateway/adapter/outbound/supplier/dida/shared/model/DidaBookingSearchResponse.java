package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * HotelBookingSearch（查单）响应（官方 booking-api/booking-search，2026-09-13 查阅）：
 * {@code Success.BookingDetailsList[]} 或 {@code Error}。
 *
 * <p><b>空列表不是「订单不存在」的证据</b>：官方明示「其他任何返回结果（如空返回…）均不能视为
 * 订单的最终处理结果」，且 3020「订单正在处理中。可能需要等待5分钟」说明建单有异步窗口。
 * 故 {@link #isEmptyResult()} 恒 false（不进 NO_INVENTORY 那一档计数），空列表由查单实现按
 * INDETERMINATE 处理。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaBookingSearchResponse implements BaseResponse {

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

    public List<DidaBookingDetails> bookings() {
        return success == null || success.getBookingDetailsList() == null
                ? List.of() : success.getBookingDetailsList();
    }

    /** 恰好一笔时返回它；零笔或多笔返回 null（多笔=同一坐标下有歧义，调用方不得挑一笔） */
    public DidaBookingDetails single() {
        List<DidaBookingDetails> list = bookings();
        return list.size() == 1 ? list.get(0) : null;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Success {

        @JsonProperty("BookingDetailsList")
        private List<DidaBookingDetails> bookingDetailsList;
    }
}
