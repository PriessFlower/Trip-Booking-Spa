package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Component
public class ExpediaUtils {

    private final ExpediaRapidProperties properties;

    /**
     * Kept only so legacy standalone main methods still compile. Signing fails safely
     * until credentials are supplied through Spring configuration.
     */
    public ExpediaUtils() {
        this(new ExpediaRapidProperties());
    }

    @Autowired
    public ExpediaUtils(ExpediaRapidProperties properties) {
        this.properties = properties;
    }

    public String signGeneration() {
        return signGeneration(Instant.now().getEpochSecond());
    }

    public String signGeneration(long timestamp) {
        properties.requireCredentials();
        String input = properties.getApiKey() + properties.getSharedSecret() + timestamp;
        String signature = sha512(input);
        return "EAN APIKey=" + properties.getApiKey()
                + ",Signature=" + signature
                + ",timestamp=" + timestamp;
    }

    private String sha512(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-512")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is unavailable", e);
        }
    }
}
