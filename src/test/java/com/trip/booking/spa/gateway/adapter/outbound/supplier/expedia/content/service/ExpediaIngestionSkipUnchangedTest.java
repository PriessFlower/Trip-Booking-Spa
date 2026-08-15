package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaPropertySnapshotMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaInactivePropertiesClient;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentClient;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentPage;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaPropertyContentRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.mapping.ExpediaPropertyContentMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaRawProperty;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.persistence.ExpediaPropertySnapshotRow;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住「内容未变则跳过写入」。
 *
 * <p>酒店静态数据是月级变化的，而每日同步此前会把筛出的酒店整行重写——每行 raw_json 约
 * 58 KB、normalized_json 约 36 KB，绝大多数写入是把同样内容又写了一遍。
 *
 * <p>本类同时钉住反向的一条，且它更重要：<b>指纹不同时必须写</b>。跳过逻辑写错的代价是
 * 该更新的没更新，而数据会静默过期、极难发现——故宁可多写，不可漏写。
 */
class ExpediaIngestionSkipUnchangedTest {

    private static final String RAW = "{\"property_id\":\"1\",\"name\":\"One\"}";
    private static final Instant FETCHED_AT = Instant.parse("2026-08-14T00:00:00Z");

    /** 指纹相同：不得调用 upsert */
    @Test
    void skipsUpsertWhenHashUnchanged() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any()))
                .thenReturn(List.of(Map.of("propertyId", "1", "rawSha256", sha256(RAW))));

        int saved = f.service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US"));

        verify(f.databaseMapper, never()).upsert(any());
        assertEquals(0, saved);
    }

    /** 指纹不同：必须写。跳过逻辑写错会导致数据静默过期，故此条最关键 */
    @Test
    void upsertsWhenHashDiffers() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any()))
                .thenReturn(List.of(Map.of("propertyId", "1", "rawSha256", "旧内容的指纹")));
        when(f.databaseMapper.upsert(any())).thenReturn(1);

        int saved = f.service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US"));

        verify(f.databaseMapper, times(1)).upsert(any());
        assertEquals(1, saved);
    }

    /** 库中尚无该酒店：必须写 */
    @Test
    void upsertsWhenPropertyIsNew() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any())).thenReturn(List.of());
        when(f.databaseMapper.upsert(any())).thenReturn(1);

        f.service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US"));

        verify(f.databaseMapper, times(1)).upsert(any());
    }

    /**
     * 读指纹失败时必须退化为全量重写，而不是跳过或中断。
     * 取不到指纹只是失去优化机会，绝不能因此漏更新。
     */
    @Test
    void writesEverythingWhenHashLookupFails() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any()))
                .thenThrow(new RuntimeException("库连接抖动"));
        when(f.databaseMapper.upsert(any())).thenReturn(1);

        f.service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US"));

        verify(f.databaseMapper, times(1)).upsert(any());
    }

    /** 指纹按语言隔离：英文未变不代表中文未变 */
    @Test
    void looksUpHashesPerLanguage() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any())).thenReturn(List.of());
        when(f.databaseMapper.upsert(any())).thenReturn(1);

        f.service.ingest(ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "zh-CN"));

        verify(f.databaseMapper).selectHashes(org.mockito.ArgumentMatchers.eq("zh-CN"), any());
    }

    /** 跳过的行不得计入 saved，否则调用方拿到的数字与实际写入不符 */
    @Test
    void doesNotCountSkippedRowsAsSaved() {
        Fixture f = new Fixture();
        when(f.databaseMapper.selectHashes(anyString(), any()))
                .thenReturn(List.of(Map.of("propertyId", "1", "rawSha256", sha256(RAW))));

        assertEquals(0, f.service.ingest(
                ExpediaPropertyContentRequest.byPropertyIds(List.of("1"), "en-US")));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 组装被测服务与其依赖 */
    private static final class Fixture {
        final ExpediaPropertySnapshotMapper databaseMapper = mock(ExpediaPropertySnapshotMapper.class);
        final ExpediaStaticDataIngestionService service;

        Fixture() {
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            ExpediaRapidProperties properties = new ExpediaRapidProperties();
            ExpediaPropertyContentClient client = mock(ExpediaPropertyContentClient.class);
            ExpediaInactivePropertiesClient inactiveClient = mock(ExpediaInactivePropertiesClient.class);
            ExpediaPropertyContentMapper contentMapper =
                    new ExpediaPropertyContentMapper(objectMapper, properties);

            when(client.fetch(any())).thenReturn(new ExpediaPropertyContentPage(
                    List.of(new ExpediaRawProperty("1", RAW, FETCHED_AT)), 1L, null));

            this.service = new ExpediaStaticDataIngestionService(
                    properties, client, inactiveClient, contentMapper, databaseMapper, objectMapper);
        }
    }
}
