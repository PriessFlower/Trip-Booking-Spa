package com.trip.booking.spa.core.api.expedia.staticdata.client;

import java.time.LocalDate;
import java.util.List;

public record ExpediaPropertyContentRequest(
        List<String> propertyIds,
        List<String> countryCodes,
        String language,
        LocalDate dateAddedStart,
        LocalDate dateAddedEnd,
        LocalDate dateUpdatedStart,
        LocalDate dateUpdatedEnd,
        String token) {

    public ExpediaPropertyContentRequest {
        propertyIds = propertyIds == null ? List.of() : List.copyOf(propertyIds);
        countryCodes = countryCodes == null ? List.of() : List.copyOf(countryCodes);
        if (propertyIds.size() > 250) {
            throw new IllegalArgumentException("Expedia Property Content accepts at most 250 property IDs per request");
        }
        requirePair(dateAddedStart, dateAddedEnd, "date_added");
        requirePair(dateUpdatedStart, dateUpdatedEnd, "date_updated");
        if (token == null && propertyIds.isEmpty() && countryCodes.isEmpty()
                && dateAddedStart == null && dateUpdatedStart == null) {
            throw new IllegalArgumentException("Property Content request must contain IDs, countries, dates, or a token");
        }
    }

    public static ExpediaPropertyContentRequest byPropertyIds(List<String> propertyIds, String language) {
        return new ExpediaPropertyContentRequest(
                propertyIds, List.of(), language, null, null, null, null, null);
    }

    public static ExpediaPropertyContentRequest updatedBetween(
            List<String> propertyIds, LocalDate start, LocalDate end, String language) {
        return new ExpediaPropertyContentRequest(
                propertyIds, List.of(), language, null, null, start, end, null);
    }

    private static void requirePair(LocalDate start, LocalDate end, String name) {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException(name + " start and end must be supplied together");
        }
        if (start != null && start.isAfter(end)) {
            throw new IllegalArgumentException(name + " start must not be after end");
        }
    }
}
