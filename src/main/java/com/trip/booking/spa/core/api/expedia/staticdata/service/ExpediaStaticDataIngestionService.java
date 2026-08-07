package com.trip.booking.spa.core.api.expedia.staticdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.core.api.expedia.mapper.ExpediaPropertySnapshotMapper;
import com.trip.booking.spa.core.api.expedia.staticdata.client.ExpediaInactivePropertiesClient;
import com.trip.booking.spa.core.api.expedia.staticdata.client.ExpediaPropertyContentClient;
import com.trip.booking.spa.core.api.expedia.staticdata.client.ExpediaPropertyContentPage;
import com.trip.booking.spa.core.api.expedia.staticdata.client.ExpediaPropertyContentRequest;
import com.trip.booking.spa.core.api.expedia.staticdata.mapping.ExpediaPropertyContentMapper;
import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaPropertyDocument;
import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaRawProperty;
import com.trip.booking.spa.core.api.expedia.staticdata.persistence.ExpediaPropertySnapshotRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExpediaStaticDataIngestionService {

    private final ExpediaRapidProperties properties;
    private final ExpediaPropertyContentClient client;
    private final ExpediaInactivePropertiesClient inactiveClient;
    private final ExpediaPropertyContentMapper contentMapper;
    private final ExpediaPropertySnapshotMapper databaseMapper;
    private final ObjectMapper objectMapper;

    public ExpediaStaticDataIngestionService(
            ExpediaRapidProperties properties,
            ExpediaPropertyContentClient client,
            ExpediaInactivePropertiesClient inactiveClient,
            ExpediaPropertyContentMapper contentMapper,
            ExpediaPropertySnapshotMapper databaseMapper,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.inactiveClient = inactiveClient;
        this.contentMapper = contentMapper;
        this.databaseMapper = databaseMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int ingestByPropertyIds(List<String> propertyIds, String language) {
        List<String> uniqueIds = propertyIds == null ? List.of() : propertyIds.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (uniqueIds.isEmpty()) {
            return 0;
        }

        int saved = 0;
        for (String lang : resolveLanguages(language)) {
            saved += ingestBatches(uniqueIds, lang);
        }
        return saved;
    }

    private int ingestBatches(List<String> uniqueIds, String language) {
        int saved = 0;
        int batchSize = properties.getStaticData().getBatchSize();
        for (int start = 0; start < uniqueIds.size(); start += batchSize) {
            List<String> batch = uniqueIds.subList(start, Math.min(start + batchSize, uniqueIds.size()));
            saved += ingest(ExpediaPropertyContentRequest.byPropertyIds(batch, language));
        }
        return saved;
    }

    /**
     * 指定语言则只拉该语言；未指定则按配置的 languages 列表逐语言拉取
     */
    private List<String> resolveLanguages(String language) {
        if (StringUtils.hasText(language)) {
            return List.of(language);
        }
        List<String> configured = properties.getStaticData().getLanguages();
        if (configured == null || configured.isEmpty()) {
            return List.of(properties.getStaticData().getLanguage());
        }
        return configured;
    }

    /**
     * Ingests the first request and every token page returned by Rapid. This also
     * supports date-added/date-updated searches built with ExpediaPropertyContentRequest.
     */
    @Transactional
    public int ingest(ExpediaPropertyContentRequest initialRequest) {
        String language = StringUtils.hasText(initialRequest.language())
                ? initialRequest.language()
                : properties.getStaticData().getLanguage();
        ExpediaPropertyContentRequest request = initialRequest;
        Set<String> seenTokens = new HashSet<>();
        int saved = 0;
        while (request != null) {
            ExpediaPropertyContentPage page = client.fetch(request);
            saved += persist(page, language);
            String token = nextToken(page);
            if (!StringUtils.hasText(token)) {
                request = null;
            } else if (!seenTokens.add(token)) {
                throw new IllegalStateException("Expedia Property Content returned a repeated pagination token");
            } else {
                request = ExpediaPropertyContentRequest.nextPage(token);
            }
        }
        return saved;
    }

    /**
     * 承接旧 deleteHotelInfo：拉取自 since 起下线的酒店并将快照置为 inactive
     *
     * @param since yyyy-MM-dd；空=7 天前（旧默认）
     * @return 已置 inactive 的 property_id 列表
     */
    public List<String> fetchAndMarkInactive(String since) {
        if (!StringUtils.hasText(since)) {
            since = java.time.LocalDate.now().minusDays(7).toString();
        }
        List<String> ids = inactiveClient.fetchInactivePropertyIds(since);
        if (ids.isEmpty()) {
            return ids;
        }
        markInactive(ids, Instant.now());
        return ids;
    }

    @Transactional
    public int markInactive(List<String> propertyIds, Instant fetchedAt) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return 0;
        }
        Timestamp timestamp = Timestamp.from(fetchedAt == null ? Instant.now() : fetchedAt);
        return propertyIds.stream()
                .filter(StringUtils::hasText)
                .mapToInt(propertyId -> databaseMapper.markInactive(propertyId, timestamp))
                .sum();
    }

    private ExpediaPropertySnapshotRow toRow(
            ExpediaRawProperty raw, ExpediaPropertyDocument document, String language) {
        try {
            return new ExpediaPropertySnapshotRow(
                    document.supplierPropertyId(),
                    language,
                    document.active(),
                    document.name(),
                    document.address() == null ? null : document.address().countryCode(),
                    document.address() == null ? null : document.address().city(),
                    document.coordinates() == null ? null : document.coordinates().latitude(),
                    document.coordinates() == null ? null : document.coordinates().longitude(),
                    document.rating() == null ? null : document.rating().property(),
                    timestamp(document.sourceAddedAt()),
                    timestamp(document.sourceUpdatedAt()),
                    timestamp(raw.fetchedAt()),
                    document.evidence().rawSha256(),
                    document.evidence().mappingVersion(),
                    raw.rawJson(),
                    objectMapper.writeValueAsString(document),
                    objectMapper.writeValueAsString(document.evidence()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize normalized Expedia property content", e);
        }
    }

    private int persist(ExpediaPropertyContentPage page, String language) {
        int saved = 0;
        for (ExpediaRawProperty raw : page.properties()) {
            ExpediaPropertyDocument document = contentMapper.map(raw);
            saved += databaseMapper.upsert(toRow(raw, document, language));
        }
        return saved;
    }

    private String nextToken(ExpediaPropertyContentPage page) {
        if (page.nextPage() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(page.nextPage())
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
