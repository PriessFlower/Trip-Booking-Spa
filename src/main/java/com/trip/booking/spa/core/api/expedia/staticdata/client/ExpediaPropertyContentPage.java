package com.trip.booking.spa.core.api.expedia.staticdata.client;

import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaRawProperty;

import java.net.URI;
import java.util.List;

public record ExpediaPropertyContentPage(
        List<ExpediaRawProperty> properties,
        Long totalResults,
        URI nextPage) {
}
