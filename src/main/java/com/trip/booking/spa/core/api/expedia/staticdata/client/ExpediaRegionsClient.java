package com.trip.booking.spa.core.api.expedia.staticdata.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.core.api.expedia.bean.response.RegionsInfoResponse;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
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
import java.util.List;

/**
 * Rapid Geography 单地区查询（GET /v3/regions/{regionId}?include=details）。
 * 请求语义照抄旧链路 RegionsAccess；HTTP 风格与 ExpediaPropertyContentClient 一致。
 */
@Slf4j
@Component
public class ExpediaRegionsClient {

    private static final String REGIONS_PATH = "/v3/regions/";

    private final ExpediaRapidProperties properties;
    private final ExpediaUtils signer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ExpediaRegionsClient(
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
     * @return 该地区详情（含 descendants），请求失败/解析失败返回 null（对齐旧链路"单点失败不断流程"的语义）
     */
    public RegionsInfoResponse fetchRegion(String regionId, String language) {
        return fetchRegion(regionId, language, "details");
    }

    /** include 可选 details / property_ids（旧 queryHotelIdByCity 语义） */
    public RegionsInfoResponse fetchRegion(String regionId, String language, String include) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(properties.getUrl().getHost())
                    .path(REGIONS_PATH + regionId)
                    .queryParam("language", language)
                    .queryParam("include", include)
                    .build().encode().toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
            headers.set(HttpHeaders.AUTHORIZATION, signer.signGeneration());
            headers.set("Customer-Ip", properties.getOwnIp());
            headers.set("Customer-Session-Id", properties.getSession());
            headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readValue(response.getBody(), RegionsInfoResponse.class);
        } catch (Exception e) {
            log.error("Expedia Regions 查询失败 regionId={} language={}", regionId, language, e);
            return null;
        }
    }
}
