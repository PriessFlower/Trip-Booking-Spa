package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExpediaPropertyDocument(
        String supplierPropertyId,
        boolean active,
        String supplySource,
        String name,
        Address address,
        Coordinates coordinates,
        Rating rating,
        Reference category,
        Reference chain,
        Reference brand,
        BusinessModel businessModel,
        String phone,
        StayInformation stayInformation,
        Instant sourceAddedAt,
        Instant sourceUpdatedAt,
        Map<String, String> descriptions,
        List<Amenity> propertyAmenities,
        List<Image> propertyImages,
        List<Room> rooms,
        List<RatePlan> ratePlans,
        List<Statistic> statistics,
        Evidence evidence) {

    public record Address(
            String line1,
            String line2,
            String city,
            String stateProvince,
            String postalCode,
            String countryCode,
            boolean obfuscationRequired) {
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude, boolean obfuscationRequired) {
    }

    public record Rating(BigDecimal property, String propertyType, BigDecimal guest, Integer guestCount) {
    }

    public record Reference(String id, String name) {
    }

    public record BusinessModel(boolean expediaCollect, boolean propertyCollect) {
    }

    public record StayInformation(
            String checkInBegin,
            String checkInEnd,
            String checkInInstructions,
            String checkInSpecialInstructions,
            String checkOutTime,
            String mandatoryFees,
            String optionalFees,
            String knowBeforeYouGo) {
    }

    public record Amenity(
            String level,
            String ownerId,
            String id,
            String name,
            String value,
            List<String> categories) {
    }

    public record Image(boolean hero, Integer category, String caption, String url) {
    }

    public record Room(
            String id,
            String name,
            String description,
            Integer squareMeters,
            Integer squareFeet,
            Occupancy occupancy,
            List<BedGroup> bedGroups,
            List<Amenity> amenities,
            List<Amenity> views,
            List<Image> images) {
    }

    public record Occupancy(Integer total, Integer adults, Integer children) {
    }

    public record BedGroup(String id, String description, List<Bed> beds) {
    }

    public record Bed(Integer quantity, String size, String type) {
    }

    public record RatePlan(String id, List<Amenity> amenities) {
    }

    public record Statistic(String id, String name, String value) {
    }

    public record Evidence(
            String source,
            String supplierPropertyId,
            Instant fetchedAt,
            String rawSha256,
            String mappingVersion,
            String mappingAlgorithmVersion,
            String internalHotelId,
            BigDecimal matchConfidence,
            List<String> matchEvidence,
            Map<String, String> fieldSources) {
    }
}
