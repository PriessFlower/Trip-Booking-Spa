package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 一位入住人（官方 booking-api/booking-confirm GuestList.GuestInfo，2026-09-13 查阅）。
 * {@code Age}「成人选填，儿童必填」；成人与儿童人数必须与 PriceConfirm 时一致，否则 3005。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaGuestInfo {

    @JsonProperty("Name")
    private DidaName name;

    @JsonProperty("IsAdult")
    private Boolean isAdult;

    @JsonProperty("Age")
    private Integer age;
}
