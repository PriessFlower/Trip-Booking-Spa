package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpediaUtilsTest {

    @Test
    void refusesToSignWithoutExternalCredentials() {
        ExpediaUtils signer = new ExpediaUtils(new ExpediaRapidProperties());

        assertThrows(IllegalStateException.class, signer::signGeneration);
    }

    @Test
    void authorizationContainsApiKeyButNeverSharedSecret() {
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setApiKey("example-api-key");
        properties.setSharedSecret("example-shared-secret");

        String authorization = new ExpediaUtils(properties).signGeneration(1234567890L);

        assertTrue(authorization.startsWith("EAN APIKey=example-api-key,Signature="));
        assertTrue(authorization.endsWith(",timestamp=1234567890"));
        assertFalse(authorization.contains("example-shared-secret"));
    }
}
