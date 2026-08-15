package com.trip.booking.spa.platform.ratelimit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.platform.util.JsonUtils;
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
        Map<String, Double> parsed = null;
        try {
            parsed = JsonUtils.decodeJson(qpsJson, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            log.error("ratelimit.qps JSON 解析抛错，回落空配置（各 key 走 default-qps）: {}", qpsJson, e);
        }
        // 必须判空而非只接异常：JsonUtils.decodeJson 解析失败时吞掉异常返回 null，
        // 只写 catch 的话 qpsMap 会被赋成 null，此后每次 qpsOf 都空指针——
        // 而 qpsOf 在所有限流调用的路径上，等于整个网关瘫痪。
        if (parsed == null) {
            log.error("ratelimit.qps 解析结果为空，回落空配置（各 key 走 default-qps={}）: {}",
                    defaultQps, qpsJson);
            qpsMap = Collections.emptyMap();
            return;
        }
        qpsMap = parsed;
        log.info("ratelimit.qps 已加载 {} 个 key，其余走 default-qps={}", parsed.size(), defaultQps);
    }

    public boolean isDistributed() {
        return "distributed".equalsIgnoreCase(mode);
    }

    public int getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    /**
     * 查某个限流 key 的 QPS，未配置则用全局默认。
     *
     * <p>此处再判一次空是有意的冗余：本方法在所有供应商调用的必经路径上，
     * 一旦抛错即全站不可用。宁可多一次判空，也不让一个配置问题变成全站故障。
     */
    public double qpsOf(String key) {
        Map<String, Double> current = qpsMap;
        return current == null ? defaultQps : current.getOrDefault(key, defaultQps);
    }
}
