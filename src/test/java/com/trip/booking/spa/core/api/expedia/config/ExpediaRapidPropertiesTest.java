package com.trip.booking.spa.core.api.expedia.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpediaRapidPropertiesTest {

    @Test
    void permitsSafeDefaultsWithoutCredentials() {
        assertDoesNotThrow(() -> new ExpediaRapidProperties().afterPropertiesSet());
    }

    @Test
    void rejectsBookingAndUnapprovedProductionEndpoint() {
        ExpediaRapidProperties booking = new ExpediaRapidProperties();
        booking.setBookingEnabled(true);
        assertThrows(IllegalStateException.class, booking::afterPropertiesSet);

        ExpediaRapidProperties production = new ExpediaRapidProperties();
        production.getUrl().setHost("https://api.ean.com");
        assertThrows(IllegalStateException.class, production::afterPropertiesSet);
    }

    @Test
    void requiresCredentialsWhenStaticIngestionIsEnabled() {
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setStaticDataEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }
}
