package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelInfoRequest {
    private Long id;
}
