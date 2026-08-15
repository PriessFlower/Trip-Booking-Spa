package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaPropertyDocument;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaRawProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExpediaPropertyContentMapper {

    private static final String SOURCE = "EXPEDIA";

    private final ObjectMapper objectMapper;
    private final ExpediaRapidProperties properties;

    public ExpediaPropertyContentMapper(ObjectMapper objectMapper, ExpediaRapidProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ExpediaPropertyDocument map(ExpediaRawProperty raw) {
        try {
            JsonNode root = objectMapper.readTree(raw.rawJson());
            String propertyId = firstNonBlank(text(root, "property_id"), raw.propertyId());
            if (propertyId == null) {
                throw new IllegalArgumentException("Expedia property content has no property_id");
            }
            if (raw.propertyId() != null && !raw.propertyId().equals(propertyId)) {
                throw new IllegalArgumentException("Expedia response property_id does not match the response key");
            }

            Map<String, String> fieldSources = fieldSources();
            ExpediaPropertyDocument.Evidence evidence = new ExpediaPropertyDocument.Evidence(
                    SOURCE,
                    propertyId,
                    raw.fetchedAt(),
                    sha256(raw.rawJson()),
                    properties.getStaticData().getMappingVersion(),
                    "unmatched-v1",
                    null,
                    null,
                    List.of(),
                    fieldSources);

            JsonNode address = root.path("address");
            JsonNode location = root.path("location");
            JsonNode coordinates = location.path("coordinates");
            JsonNode ratings = root.path("ratings");
            JsonNode propertyRating = ratings.path("property");
            JsonNode guestRating = ratings.path("guest");
            JsonNode checkIn = root.path("checkin");
            JsonNode fees = root.path("fees");

            return new ExpediaPropertyDocument(
                    propertyId,
                    true,
                    firstNonBlank(text(root, "supply_source"), properties.getStaticData().getSupplySource()),
                    text(root, "name"),
                    new ExpediaPropertyDocument.Address(
                            text(address, "line_1"),
                            text(address, "line_2"),
                            text(address, "city"),
                            text(address, "state_province_name"),
                            text(address, "postal_code"),
                            text(address, "country_code"),
                            bool(address, "obfuscation_required")),
                    new ExpediaPropertyDocument.Coordinates(
                            decimal(coordinates, "latitude"),
                            decimal(coordinates, "longitude"),
                            bool(location, "obfuscation_required")),
                    new ExpediaPropertyDocument.Rating(
                            decimal(propertyRating, "rating"),
                            text(propertyRating, "type"),
                            decimal(guestRating, "overall"),
                            integer(guestRating, "count")),
                    reference(root.path("category")),
                    reference(root.path("chain")),
                    reference(root.path("brand")),
                    new ExpediaPropertyDocument.BusinessModel(
                            bool(root.path("business_model"), "expedia_collect"),
                            bool(root.path("business_model"), "property_collect")),
                    text(root, "phone"),
                    new ExpediaPropertyDocument.StayInformation(
                            text(checkIn, "begin_time"),
                            text(checkIn, "end_time"),
                            text(checkIn, "instructions"),
                            text(checkIn, "special_instructions"),
                            text(root.path("checkout"), "time"),
                            text(fees, "mandatory"),
                            text(fees, "optional"),
                            text(root.path("policies"), "know_before_you_go")),
                    instant(root.path("dates"), "added"),
                    instant(root.path("dates"), "updated"),
                    stringMap(root.path("descriptions")),
                    amenities(root.path("amenities"), "PROPERTY", propertyId),
                    images(root.path("images")),
                    rooms(root.path("rooms")),
                    ratePlans(root.path("rates")),
                    statistics(root.path("statistics")),
                    evidence);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to map Expedia property content", e);
        }
    }

    private List<ExpediaPropertyDocument.Room> rooms(JsonNode node) {
        if (!node.isObject()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.Room> result = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode room = entry.getValue();
            String roomId = firstNonBlank(text(room, "id"), entry.getKey());
            JsonNode area = room.path("area");
            JsonNode maximum = room.path("occupancy").path("max_allowed");
            result.add(new ExpediaPropertyDocument.Room(
                    roomId,
                    text(room, "name"),
                    text(room.path("descriptions"), "overview"),
                    integer(area, "square_meters"),
                    integer(area, "square_feet"),
                    new ExpediaPropertyDocument.Occupancy(
                            integer(maximum, "total"),
                            integer(maximum, "adults"),
                            integer(maximum, "children")),
                    bedGroups(room.path("bed_groups")),
                    amenities(room.path("amenities"), "ROOM", roomId),
                    amenities(room.path("views"), "ROOM_VIEW", roomId),
                    images(room.path("images"))));
        });
        return List.copyOf(result);
    }

    private List<ExpediaPropertyDocument.BedGroup> bedGroups(JsonNode node) {
        if (!node.isObject()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.BedGroup> result = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode group = entry.getValue();
            List<ExpediaPropertyDocument.Bed> beds = new ArrayList<>();
            group.path("configuration").forEach(bed -> beds.add(new ExpediaPropertyDocument.Bed(
                    integer(bed, "quantity"), text(bed, "size"), text(bed, "type"))));
            result.add(new ExpediaPropertyDocument.BedGroup(
                    firstNonBlank(text(group, "id"), entry.getKey()),
                    text(group, "description"),
                    List.copyOf(beds)));
        });
        return List.copyOf(result);
    }

    private List<ExpediaPropertyDocument.RatePlan> ratePlans(JsonNode node) {
        if (!node.isObject()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.RatePlan> result = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            String rateId = firstNonBlank(text(entry.getValue(), "id"), entry.getKey());
            result.add(new ExpediaPropertyDocument.RatePlan(
                    rateId,
                    amenities(entry.getValue().path("amenities"), "RATE_PLAN", rateId)));
        });
        return List.copyOf(result);
    }

    private List<ExpediaPropertyDocument.Amenity> amenities(JsonNode node, String level, String ownerId) {
        if (!node.isObject()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.Amenity> result = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode amenity = entry.getValue();
            List<String> categories = new ArrayList<>();
            amenity.path("categories").forEach(category -> categories.add(category.asText()));
            result.add(new ExpediaPropertyDocument.Amenity(
                    level,
                    ownerId,
                    firstNonBlank(text(amenity, "id"), entry.getKey()),
                    text(amenity, "name"),
                    text(amenity, "value"),
                    List.copyOf(categories)));
        });
        return List.copyOf(result);
    }

    private List<ExpediaPropertyDocument.Image> images(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.Image> result = new ArrayList<>();
        node.forEach(image -> result.add(new ExpediaPropertyDocument.Image(
                bool(image, "hero_image"),
                integer(image, "category"),
                text(image, "caption"),
                imageUrl(image.path("links")))));
        return List.copyOf(result);
    }

    private String imageUrl(JsonNode links) {
        for (String size : List.of("1000px", "350px", "200px", "70px")) {
            String href = text(links.path(size), "href");
            if (href != null) {
                return href;
            }
        }
        return null;
    }

    private List<ExpediaPropertyDocument.Statistic> statistics(JsonNode node) {
        if (!node.isObject()) {
            return List.of();
        }
        List<ExpediaPropertyDocument.Statistic> result = new ArrayList<>();
        node.fields().forEachRemaining(entry -> result.add(new ExpediaPropertyDocument.Statistic(
                firstNonBlank(text(entry.getValue(), "id"), entry.getKey()),
                text(entry.getValue(), "name"),
                text(entry.getValue(), "value"))));
        return List.copyOf(result);
    }

    private ExpediaPropertyDocument.Reference reference(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new ExpediaPropertyDocument.Reference(text(node, "id"), text(node, "name"));
    }

    private Map<String, String> stringMap(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isValueNode()) {
                result.put(field.getKey(), field.getValue().asText());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, String> fieldSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("supplierPropertyId", "$.property_id");
        sources.put("name", "$.name");
        sources.put("address", "$.address");
        sources.put("coordinates", "$.location.coordinates");
        sources.put("rating", "$.ratings");
        sources.put("category", "$.category");
        sources.put("chain", "$.chain");
        sources.put("brand", "$.brand");
        sources.put("businessModel", "$.business_model");
        sources.put("propertyAmenities", "$.amenities");
        sources.put("propertyImages", "$.images");
        sources.put("rooms", "$.rooms");
        sources.put("roomAmenities", "$.rooms.*.amenities");
        sources.put("ratePlanAmenities", "$.rates.*.amenities");
        sources.put("sourceUpdatedAt", "$.dates.updated");
        return Collections.unmodifiableMap(sources);
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).isContainerNode()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).isNumber() ? node.get(field).intValue() : parseInteger(node.get(field).asText());
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean bool(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).asBoolean(false);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
