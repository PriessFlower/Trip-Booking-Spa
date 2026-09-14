package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * HotelBookingCancel（预取消）请求体（官方 booking-api/booking-cancel/booking-pre-cancel，
 * 2026-09-13 查阅）。「请注意，这个 api 是预取消，只调用这个 api 是不足以真正取消一个订单的」
 * ——它只回罚金与取消确认号，真取消在下一步 HotelBookingCancelConfirm。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaBookingCancelRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("BookingID")
    private String bookingId;
}
