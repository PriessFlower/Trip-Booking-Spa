package com.trip.booking.spa.core.ratelimit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;

/**
 * 限流配置（Nacos 一处配置，@RefreshScope 改配置热生效）。
 * 风格对齐 NacosRuntimeConfig：@Value + JSON 字符串。
 *
 * ratelimit:
 *   mode: local              # local(Guava 单机) | distributed(Redisson 跨实例)
 *   default-qps: 10          # 未在下表配置的 key 用这个
 *   acquire-timeout-ms: 5000 # tryAcquire 等待上限
 *   qps: '{"GLOBAL_LIMIT:EXPEDIA:SPA_SUPPLIER_API_PRODUCT_PRICES":500}'
 *
 * qps 表的 key = BaseHttpAccess.buildGlobalLimitKey() 产出的 供应商_接口 标识。
 */
@Slf4j
@Component
@RefreshScope
public class RateLimitProperties {

    @Value("${ratelimit.mode:local}")
    private String mode;

    @Value("${ratelimit.default-qps:10}")
    private double defaultQps;

    @Value("${ratelimit.acquire-timeout-ms:5000}")
    private int acquireTimeoutMs;

    @Value("${ratelimit.qps:}")
    private String qpsJson;

    private volatile Map<String, Double> qpsMap = Collections.emptyMap();

    @PostConstruct
    public void init() {
        if (StringUtils.isBlank(qpsJson)) {
            qpsMap = Collections.emptyMap();
            return;
        }
        try {
            qpsMap = JsonUtils.decodeJson(qpsJson, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            log.error("ratelimit.qps JSON parse failed, fallback to empty: {}", qpsJson, e);
            qpsMap = Collections.emptyMap();
        }
    }

    public boolean isDistributed() {
        return "distributed".equalsIgnoreCase(mode);
    }

    public int getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    /** 查某个限流 key 的 QPS，未配置则用全局默认 */
    public double qpsOf(String key) {
        return qpsMap.getOrDefault(key, defaultQps);
    }
}
