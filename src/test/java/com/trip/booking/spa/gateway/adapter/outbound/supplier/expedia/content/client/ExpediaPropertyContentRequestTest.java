package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpediaPropertyContentRequestTest {

    @Test
    void acceptsAtMostTwoHundredAndFiftyPropertyIds() {
        assertDoesNotThrow(() -> ExpediaPropertyContentRequest.byPropertyIds(
                Collections.nCopies(250, "1"), "en-US"));
        assertThrows(IllegalArgumentException.class, () -> ExpediaPropertyContentRequest.byPropertyIds(
                Collections.nCopies(251, "1"), "en-US"));
    }

    @Test
    void requiresCompleteAndOrderedDateWindows() {
        assertThrows(IllegalArgumentException.class, () -> new ExpediaPropertyContentRequest(
                List.of("1"), List.of(), "en-US",
                null, null, LocalDate.parse("2026-08-01"), null, null));
        assertThrows(IllegalArgumentException.class, () -> ExpediaPropertyContentRequest.updatedBetween(
                List.of("1"), LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-01"), "en-US"));
    }
}
