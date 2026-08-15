package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaPropertySnapshotMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentClient;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaInactivePropertiesClient;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentPage;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.mapping.ExpediaPropertyContentMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaRawProperty;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpediaStaticDataIngestionServiceTest {

    @Test
    void followsPaginationTokensAndUpsertsEveryProperty() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        ExpediaPropertyContentClient client = mock(ExpediaPropertyContentClient.class);
        ExpediaInactivePropertiesClient inactiveClient = mock(ExpediaInactivePropertiesClient.class);
        ExpediaPropertySnapshotMapper databaseMapper = mock(ExpediaPropertySnapshotMapper.class);
        ExpediaPropertyContentMapper contentMapper = new ExpediaPropertyContentMapper(objectMapper, properties);
        ExpediaStaticDataIngestionService service = new ExpediaStaticDataIngestionService(
                properties, client, inactiveClient, contentMapper, databaseMapper, objectMapper);

        Instant fetchedAt = Instant.parse("2026-08-06T00:00:00Z");
        ExpediaPropertyContentPage first = new ExpediaPropertyContentPage(
                List.of(new ExpediaRawProperty("1", "{\"property_id\":\"1\",\"name\":\"One\"}", fetchedAt)),
                2L,
                URI.create("https://test.ean.com/v3/properties/content?token=next-token"));
        ExpediaPropertyContentPage second = new ExpediaPropertyContentPage(
                List.of(new ExpediaRawProperty("2", "{\"property_id\":\"2\",\"name\":\"Two\"}", fetchedAt)),
                2L,
                null);
        when(client.fetch(any(ExpediaPropertyContentRequest.class))).thenReturn(first, second);
        when(databaseMapper.upsert(any())).thenReturn(1);

        int saved = service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US"));

        assertEquals(2, saved);
        verify(databaseMapper, org.mockito.Mockito.times(2)).upsert(any());
        verify(client, org.mockito.Mockito.times(2)).fetch(any(ExpediaPropertyContentRequest.class));
    }
}
