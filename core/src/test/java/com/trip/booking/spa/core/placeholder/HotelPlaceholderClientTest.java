package com.trip.booking.spa.core.placeholder;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotelPlaceholderClientTest {

    @Test
    void hotelBaseOperationsFailExplicitly() {
        UnsupportedOperationException error = assertThrows(
                UnsupportedOperationException.class,
                () -> new HotelBasePlaceholderClient().saveHotelDetails(Collections.emptyList()));

        assertTrue(error.getMessage().contains("hotel-base-intl was removed"));
    }

    @Test
    void hotelInfoOperationsFailExplicitly() {
        UnsupportedOperationException error = assertThrows(
                UnsupportedOperationException.class,
                () -> new HotelInfoPlaceholderClient().saveHotelInfo(Collections.emptyList()));

        assertTrue(error.getMessage().contains("hotel-info-intl was removed"));
    }
}
