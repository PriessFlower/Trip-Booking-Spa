package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.model.ExpediaRawProperty;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExpediaPropertyContentClient {

    private static final String CONTENT_PATH = "/v3/properties/content";

    /** 限流 key，格式对齐 access 层 GLOBAL_LIMIT:供应商:接口 */
    private static final String RATE_LIMIT_KEY = "GLOBAL_LIMIT:EXPEDIA:STATIC_CONTENT";

    private final ExpediaRapidProperties properties;
    private final ExpediaUtils signer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ExpediaPropertyContentClient(
            ExpediaRapidProperties properties,
            ExpediaUtils signer,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * 闸口 supplier.expedia.static-data-enabled：是否允许调用 Expedia 静态内容接口。
     * 误开风险=误在开发或测试环境消耗 Expedia 静态接口配额；
     * 误关风险=酒店与房型目录无法摄取或更新，播种与每日增量同步全部失败；
     * 执行面：全部节点的静态摄取路径（BackDoor 手动摄取与 ExpediaHotelSyncTask 定时同步）。
     */
    public ExpediaPropertyContentPage fetch(ExpediaPropertyContentRequest request) {
        if (!properties.isStaticDataEnabled()) {
            log.warn("[gate] supplier.expedia.static-data-enabled=false，拒绝静态内容摄取: propertyIds={}, language={}",
                    request.propertyIds(), request.language());
            throw new IllegalStateException("Expedia static data ingestion is disabled");
        }
        properties.requireCredentials();
        URI uri = buildUri(request);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        headers.set(HttpHeaders.AUTHORIZATION, signer.signGeneration());
        headers.set("Customer-Ip", properties.getOwnIp());
        headers.set("Customer-Session-Id", properties.getSession());
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());

        // 统一限流（阻塞式，批量摄取平滑放行）：全量/增量拉取的闸门，QPS 配在 Nacos ratelimit.qps
        RateLimitHolder.get().acquire(RATE_LIMIT_KEY);
        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return parse(response);
    }

    private URI buildUri(ExpediaPropertyContentRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl().getHost())
                .path(CONTENT_PATH);
        if (StringUtils.hasText(request.token())) {
            builder.queryParam("token", request.token());
            return builder.build().encode().toUri();
        }

        builder.queryParam("language", firstNonBlank(
                request.language(), properties.getStaticData().getLanguage()));
        builder.queryParam("supply_source", properties.getStaticData().getSupplySource());
        request.propertyIds().forEach(value -> builder.queryParam("property_id", value));
        request.countryCodes().forEach(value -> builder.queryParam("country_code", value));
        addDate(builder, "date_added_start", request.dateAddedStart());
        addDate(builder, "date_added_end", request.dateAddedEnd());
        addDate(builder, "date_updated_start", request.dateUpdatedStart());
        addDate(builder, "date_updated_end", request.dateUpdatedEnd());
        return builder.build().encode().toUri();
    }

    private ExpediaPropertyContentPage parse(ResponseEntity<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Expedia Property Content response is not a JSON object");
            }
            Instant fetchedAt = Instant.now();
            List<ExpediaRawProperty> snapshots = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                snapshots.add(new ExpediaRawProperty(
                        field.getKey(), objectMapper.writeValueAsString(field.getValue()), fetchedAt));
            }
            return new ExpediaPropertyContentPage(
                    List.copyOf(snapshots),
                    longHeader(response.getHeaders(), "Pagination-Total-Results"),
                    nextPage(response.getHeaders().getFirst(HttpHeaders.LINK)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Expedia Property Content response", e);
        }
    }

    private void addDate(UriComponentsBuilder builder, String name, Object value) {
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private Long longHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private URI nextPage(String link) {
        if (!StringUtils.hasText(link)) {
            return null;
        }
        int start = link.indexOf('<');
        int end = link.indexOf('>');
        if (start < 0 || end <= start) {
            return null;
        }
        URI uri = URI.create(link.substring(start + 1, end));
        URI configured = URI.create(properties.getUrl().getHost());
        if (!configured.getHost().equalsIgnoreCase(uri.getHost())) {
            throw new IllegalStateException("Expedia pagination link points to an unexpected host");
        }
        return uri;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
