package com.trip.booking.spa.core.api.huitravel.bean.price.availability;

import lombok.Data;

import java.util.List;

@Data
public class AvailabilityResult {
    private List<Price> prices;
}
