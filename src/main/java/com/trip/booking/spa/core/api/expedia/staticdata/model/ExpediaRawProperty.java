package com.trip.booking.spa.core.api.expedia.staticdata.model;

import java.time.Instant;

public record ExpediaRawProperty(String propertyId, String rawJson, Instant fetchedAt) {
}
