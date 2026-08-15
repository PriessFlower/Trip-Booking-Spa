package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model;

import java.time.Instant;

public record ExpediaRawProperty(String propertyId, String rawJson, Instant fetchedAt) {
}
