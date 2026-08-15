package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaRawProperty;

import java.net.URI;
import java.util.List;

public record ExpediaPropertyContentPage(
        List<ExpediaRawProperty> properties,
        Long totalResults,
        URI nextPage) {
}
