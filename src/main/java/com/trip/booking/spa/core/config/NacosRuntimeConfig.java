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
 * Nacos 下发的运维配置（键名规则见 PROJECT.md §2.7）。
 *
 * <p>集合类取值沿用 Apollo 时期的 JSON 字符串表示，迁移到 Nacos 时内容无需改动。</p>
 */
@Slf4j
@Getter
@Component
@RefreshScope
public class NacosRuntimeConfig {

    /** 启用我方价格缓存的供应商 */
    @Value("${cache.price.suppliers:}")
    private String cachePriceSuppliersJson;

    /** 启用我方价格缓存的酒店白名单；某供应商对应空列表表示其全部酒店均走缓存 */
    @Value("${cache.price.hotels:}")
    private String cachePriceHotelsJson;

    /** 时区建档流程的供应商范围 */
    @Value("${task.timezone-init.suppliers:}")
    private String timezoneInitSuppliersJson;

    /** 是否要求道旅返回实时价（false 则接受其缓存价） */
    @Value("${supplier.didatravel.real-time-price:false}")
    private boolean didatravelRealTimePrice;

    private List<Integer> cachePriceSuppliers = Collections.emptyList();
    private Map<Integer, List<String>> cachePriceHotels = Collections.emptyMap();
    private List<String> timezoneInitSuppliers = Collections.emptyList();

    @PostConstruct
    void parseStructuredValues() {
        cachePriceSuppliers = valueOrEmpty(
                JsonUtils.decodeJson(cachePriceSuppliersJson, new TypeReference<List<Integer>>() {}),
                Collections.emptyList(),
                "cache.price.suppliers");
        cachePriceHotels = valueOrEmpty(
                JsonUtils.decodeJson(cachePriceHotelsJson, new TypeReference<Map<Integer, List<String>>>() {}),
                Collections.emptyMap(),
                "cache.price.hotels");
        timezoneInitSuppliers = valueOrEmpty(
                JsonUtils.decodeJson(timezoneInitSuppliersJson, new TypeReference<List<String>>() {}),
                Collections.emptyList(),
                "task.timezone-init.suppliers");
    }

    private <T> T valueOrEmpty(T value, T emptyValue, String key) {
        if (value == null) {
            log.warn("Nacos config {} is empty or invalid; using an empty value", key);
            return emptyValue;
        }
        return value;
    }
}
