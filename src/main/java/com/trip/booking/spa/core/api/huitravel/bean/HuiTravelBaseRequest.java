package com.trip.booking.spa.core.api.huitravel.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class HuiTravelBaseRequest<T> {
    private Head head;

    private T data;
}
