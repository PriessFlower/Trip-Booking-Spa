package com.trip.booking.spa.core.config;

import com.trip.booking.spa.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime switches loaded from Nacos Config.
 *
 * <p>The list/map values keep the JSON representation previously used in Apollo,
 * so they can be migrated to Nacos without changing their content.</p>
 */
@Slf4j
@Getter
@Component
@RefreshScope
public class NacosRuntimeConfig {

    @Value("${query.cache.supplier:}")
    private String queryCacheSupplierJson;

    @Value("${supplier.hotel.cache.map:}")
    private String supplierHotelCacheMapJson;

    @Value("${supplier.query.timezone:}")
    private String supplierQueryTimezoneJson;

    @Value("${didatravel.query.cache:false}")
    private boolean didatravelQueryCache;

    private List<Integer> queryCacheSuppliers = Collections.emptyList();
    private Map<Integer, List<String>> supplierHotelCacheMap = Collections.emptyMap();
    private List<String> supplierQueryTimezones = Collections.emptyList();

    @PostConstruct
    void parseStructuredValues() {
        queryCacheSuppliers = valueOrEmpty(
                JsonUtils.decodeJson(queryCacheSupplierJson, new TypeReference<List<Integer>>() {}),
                Collections.emptyList(),
                "query.cache.supplier");
        supplierHotelCacheMap = valueOrEmpty(
                JsonUtils.decodeJson(supplierHotelCacheMapJson, new TypeReference<Map<Integer, List<String>>>() {}),
                Collections.emptyMap(),
                "supplier.hotel.cache.map");
        supplierQueryTimezones = valueOrEmpty(
                JsonUtils.decodeJson(supplierQueryTimezoneJson, new TypeReference<List<String>>() {}),
                Collections.emptyList(),
                "supplier.query.timezone");
    }

    private <T> T valueOrEmpty(T value, T emptyValue, String key) {
        if (value == null) {
            log.warn("Nacos config {} is empty or invalid; using an empty value", key);
            return emptyValue;
        }
        return value;
    }
}
