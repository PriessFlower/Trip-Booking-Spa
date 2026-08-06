package com.trip.booking.spa.core.api.expedia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "expedia")
public class ExpediaRapidProperties {

    private String apiKey;
    private String sharedSecret;
    private String session = "trip-booking-spa";
    private String ownIp = "127.0.0.1";
    private String userAgent = "trip-booking-spa/0.0.1";
    private boolean bookingEnabled;
    private Url url = new Url();
    private StaticData staticData = new StaticData();

    public void requireCredentials() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(sharedSecret)) {
            throw new IllegalStateException(
                    "Expedia Rapid credentials are missing; set EXPEDIA_API_KEY and EXPEDIA_SHARED_SECRET");
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getOwnIp() {
        return ownIp;
    }

    public void setOwnIp(String ownIp) {
        this.ownIp = ownIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public StaticData getStaticData() {
        return staticData;
    }

    public void setStaticData(StaticData staticData) {
        this.staticData = staticData;
    }

    public static class Url {
        private String host = "https://test.ean.com";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }

    public static class StaticData {
        private int batchSize = 250;
        private String language = "en-US";
        private String supplySource = "expedia";
        private String mappingVersion = "expedia-content-v1";

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize < 1 || batchSize > 250) {
                throw new IllegalArgumentException("expedia.static-data.batch-size must be between 1 and 250");
            }
            this.batchSize = batchSize;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getSupplySource() {
            return supplySource;
        }

        public void setSupplySource(String supplySource) {
            this.supplySource = supplySource;
        }

        public String getMappingVersion() {
            return mappingVersion;
        }

        public void setMappingVersion(String mappingVersion) {
            this.mappingVersion = mappingVersion;
        }
    }
}
