package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * HotelBookingCancelConfirm（确认取消）请求体（官方 booking-api/booking-cancel/booking-cancel-confirm，
 * 2026-09-13 查阅）：用预取消回的 {@code ConfirmID}（10 分钟有效，过期报 3007）做最后确认。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaBookingCancelConfirmRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("BookingID")
    private String bookingId;

    @JsonProperty("ConfirmID")
    private String confirmId;

    /** 「客户备注，可以填取消原因之类的」 */
    @JsonProperty("Description")
    private String description;
}
