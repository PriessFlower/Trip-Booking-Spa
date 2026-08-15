package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ExpediaPropertySnapshotRow {

    private final String propertyId;
    private final String language;
    private final boolean active;
    private final String name;
    private final String countryCode;
    private final String city;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final BigDecimal starRating;
    private final Timestamp sourceAddedAt;
    private final Timestamp sourceUpdatedAt;
    private final Timestamp fetchedAt;
    private final String rawSha256;
    private final String mappingVersion;
    private final String rawJson;
    private final String normalizedJson;
    private final String evidenceJson;

    public ExpediaPropertySnapshotRow(
            String propertyId,
            String language,
            boolean active,
            String name,
            String countryCode,
            String city,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal starRating,
            Timestamp sourceAddedAt,
            Timestamp sourceUpdatedAt,
            Timestamp fetchedAt,
            String rawSha256,
            String mappingVersion,
            String rawJson,
            String normalizedJson,
            String evidenceJson) {
        this.propertyId = propertyId;
        this.language = language;
        this.active = active;
        this.name = name;
        this.countryCode = countryCode;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.starRating = starRating;
        this.sourceAddedAt = sourceAddedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.fetchedAt = fetchedAt;
        this.rawSha256 = rawSha256;
        this.mappingVersion = mappingVersion;
        this.rawJson = rawJson;
        this.normalizedJson = normalizedJson;
        this.evidenceJson = evidenceJson;
    }

    public String getPropertyId() { return propertyId; }

    public String getLanguage() { return language; }
    public boolean isActive() { return active; }
    public String getName() { return name; }
    public String getCountryCode() { return countryCode; }
    public String getCity() { return city; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getStarRating() { return starRating; }
    public Timestamp getSourceAddedAt() { return sourceAddedAt; }
    public Timestamp getSourceUpdatedAt() { return sourceUpdatedAt; }
    public Timestamp getFetchedAt() { return fetchedAt; }
    public String getRawSha256() { return rawSha256; }
    public String getMappingVersion() { return mappingVersion; }
    public String getRawJson() { return rawJson; }
    public String getNormalizedJson() { return normalizedJson; }
    public String getEvidenceJson() { return evidenceJson; }
}
