package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 一间房的入住人清单（官方 booking-api/booking-confirm GuestList 元素，2026-09-13 查阅）。{@code RoomNum} 从 1 起 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaGuestRoom {

    @JsonProperty("RoomNum")
    private Integer roomNum;

    @JsonProperty("GuestInfo")
    private List<DidaGuestInfo> guestInfo;
}
