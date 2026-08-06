package com.trip.booking.spa.core.api.expedia.staticdata.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaPropertyDocument;
import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaRawProperty;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExpediaPropertyContentMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ExpediaPropertyContentMapper mapper =
            new ExpediaPropertyContentMapper(objectMapper, new ExpediaRapidProperties());

    @Test
    void mapsPropertyRoomAndRatePlanAmenitiesWithoutFlatteningTheirLevels() {
        String rawJson = """
                {
                  "property_id": "10001",
                  "name": "Example Hotel",
                  "supply_source": "expedia",
                  "address": {
                    "line_1": "1 Example Road",
                    "city": "Bangkok",
                    "country_code": "TH",
                    "obfuscation_required": false
                  },
                  "location": {
                    "coordinates": {"latitude": 13.7563, "longitude": 100.5018},
                    "obfuscation_required": false
                  },
                  "ratings": {"property": {"rating": "4.5", "type": "Star"}},
                  "category": {"id": "1", "name": "Hotel"},
                  "business_model": {"expedia_collect": true, "property_collect": false},
                  "amenities": {
                    "3861": {"id": "3861", "name": "Free self parking", "categories": ["parking"]}
                  },
                  "images": [{
                    "hero_image": true,
                    "category": 1000,
                    "caption": "Exterior",
                    "links": {"1000px": {"href": "https://images.example/property.jpg"}}
                  }],
                  "rooms": {
                    "20001": {
                      "id": "20001",
                      "name": "Deluxe Room",
                      "descriptions": {"overview": "One king bed"},
                      "amenities": {"6176": {"id": "6176", "name": "Non-Smoking"}},
                      "bed_groups": {
                        "30001": {
                          "id": "30001",
                          "description": "1 King Bed",
                          "configuration": [{"quantity": 1, "size": "King", "type": "KingBed"}]
                        }
                      },
                      "occupancy": {"max_allowed": {"total": 3, "adults": 2, "children": 1}}
                    }
                  },
                  "rates": {
                    "40001": {
                      "id": "40001",
                      "amenities": {
                        "1073742786": {
                          "id": "1073742786",
                          "name": "Free breakfast",
                          "categories": ["free_breakfast"]
                        }
                      }
                    }
                  },
                  "dates": {
                    "added": "2025-01-01T00:00:00Z",
                    "updated": "2025-02-02T03:04:05Z"
                  }
                }
                """;

        ExpediaPropertyDocument result = mapper.map(new ExpediaRawProperty(
                "10001", rawJson, Instant.parse("2026-08-06T00:00:00Z")));

        assertEquals("10001", result.supplierPropertyId());
        assertEquals("TH", result.address().countryCode());
        assertEquals("PROPERTY", result.propertyAmenities().get(0).level());
        assertEquals("ROOM", result.rooms().get(0).amenities().get(0).level());
        assertEquals("RATE_PLAN", result.ratePlans().get(0).amenities().get(0).level());
        assertEquals("KingBed", result.rooms().get(0).bedGroups().get(0).beds().get(0).type());
        assertEquals("$.rates.*.amenities", result.evidence().fieldSources().get("ratePlanAmenities"));
        assertEquals(64, result.evidence().rawSha256().length());
        assertNotNull(result.sourceUpdatedAt());
        assertFalse(result.evidence().rawSha256().isBlank());
    }
}
