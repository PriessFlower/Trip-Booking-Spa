package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rapid Inactive Properties（GET /v3/properties/inactive?since=yyyy-MM-dd）。
 * 请求语义照抄旧链路 HotelRemoveAccess；Link 头分页处理与 ExpediaPropertyContentClient 一致。
 */
@Slf4j
@Component
public class ExpediaInactivePropertiesClient {

    private static final String INACTIVE_PATH = "/v3/properties/inactive";

    private final ExpediaRapidProperties properties;
    private final ExpediaUtils signer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ExpediaInactivePropertiesClient(
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
     * @param since 起始日期（yyyy-MM-dd），返回自该日起下线的全部 property_id（自动翻页）
     */
    public List<String> fetchInactivePropertyIds(String since) {
        List<String> ids = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl().getHost())
                .path(INACTIVE_PATH)
                .queryParam("since", since)
                .build().encode().toUri();
        while (uri != null) {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root != null && root.isArray()) {
                    root.forEach(node -> {
                        JsonNode id = node.get("property_id");
                        if (id != null) {
                            ids.add(id.asText());
                        }
                    });
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse Expedia inactive properties response", e);
            }
            uri = nextPage(response.getHeaders().getFirst(HttpHeaders.LINK), seenTokens);
        }
        return ids;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        headers.set(HttpHeaders.AUTHORIZATION, signer.signGeneration());
        headers.set("Customer-Ip", properties.getOwnIp());
        headers.set("Customer-Session-Id", properties.getSession());
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
        return headers;
    }

    private URI nextPage(String linkHeader, Set<String> seenTokens) {
        if (linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
            return null;
        }
        int start = linkHeader.indexOf('<');
        int end = linkHeader.indexOf('>');
        if (start < 0 || end <= start) {
            return null;
        }
        String href = linkHeader.substring(start + 1, end);
        if (!seenTokens.add(href)) {
            throw new IllegalStateException("Expedia inactive properties returned a repeated page link");
        }
        if (href.startsWith("http")) {
            return URI.create(href);
        }
        return URI.create(properties.getUrl().getHost() + href);
    }
}
