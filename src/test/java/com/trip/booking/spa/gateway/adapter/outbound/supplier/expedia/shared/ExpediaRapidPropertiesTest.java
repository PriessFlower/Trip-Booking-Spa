package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpediaRapidPropertiesTest {

    @Test
    void permitsSafeDefaultsWithoutCredentials() {
        assertDoesNotThrow(() -> new ExpediaRapidProperties().afterPropertiesSet());
    }

    @Test
    void rejectsUnapprovedProductionEndpoint() {
        ExpediaRapidProperties production = new ExpediaRapidProperties();
        production.getUrl().setHost("https://api.ean.com");
        assertThrows(IllegalStateException.class, production::afterPropertiesSet);
    }

    /** 测试端点下单为沙箱行为，应允许，以便验证下单链路 */
    @Test
    void permitsBookingAgainstTestEndpoint() {
        ExpediaRapidProperties properties = withCredentials();
        properties.setBookingEnabled(true);

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    /** 生产端点下单在认证通过前必须拒绝启动，纵使生产端点本身已获授权 */
    @Test
    void rejectsBookingAgainstProductionEndpoint() {
        ExpediaRapidProperties properties = withCredentials();
        properties.getUrl().setHost("https://api.ean.com");
        properties.setProductionEndpointEnabled(true);
        properties.setBookingEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    /** 启用下单但缺凭证应拒绝启动 */
    @Test
    void requiresCredentialsWhenBookingIsEnabled() {
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setBookingEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    private ExpediaRapidProperties withCredentials() {
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setApiKey("test-key");
        properties.setSharedSecret("test-secret");
        return properties;
    }

    @Test
    void requiresCredentialsWhenStaticIngestionIsEnabled() {
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setStaticDataEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }
}
