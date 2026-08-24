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
import java.util.HashMap;
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
        checkBucketSums(parsed);
    }

    /**
     * 校验「同一接口的各用途子桶之和 ≤ 该接口桶」。违反只报错不改值——配置的事在配置里修，
     * 运行期偷偷改数只会让人对着 Nacos 猜为什么不生效。
     *
     * <p>这条不变式此前被放在<b>账号层</b>："艺龙各接口配额之和不得超过账号总额 10"。而艺龙
     * 没有账号总额，是按方法各自限（{@code hotel.detail} 单独 15，2026-08-24 核对开放平台
     * 接口能力页）。那个虚构的总额把查价长期锁在能力的 40%。层级搬到接口之后这条才真能校。
     */
    private void checkBucketSums(Map<String, Double> map) {
        Map<String, Double> sums = new HashMap<>();
        for (Map.Entry<String, Double> e : map.entrySet()) {
            int i = e.getKey().lastIndexOf(':');
            if (i < 0) {
                continue;
            }
            String parent = e.getKey().substring(0, i);
            // 父键也在表里才算子桶；接口键自己截出来的是 GLOBAL_LIMIT:供应商，不是键，会被跳过
            if (map.containsKey(parent)) {
                sums.merge(parent, e.getValue(), Double::sum);
            }
        }
        sums.forEach((parent, sum) -> {
            double total = map.get(parent);
            if (sum > total) {
                log.error("[gate] 限流配置越界：{} 的各用途子桶之和 {} 超过接口桶 {}——子桶是内部分配、"
                        + "接口桶是对供应商的承诺，之和越界即承诺可能被打破。请到 Nacos 的 "
                        + "ratelimit.qps 调平", parent, sum, total);
            }
        });
    }

    /**
     * 该 key 是否被显式登记过。
     *
     * <p>用途子桶专用：<b>没登记就等于不分配</b>，通道层只扣接口桶。不能拿 {@link #qpsOf}
     * 判断——它对未登记的 key 回落 {@code default-qps}，于是"忘了配子桶"会表现成"子桶有个
     * 很大的额度"，比不设子桶更糟。这也让代码可以先于配置发布：新键还没进 Nacos 时行为与
     * 改动前完全一致（只走接口桶），而不是按默认值放飞。
     */
    public boolean isRegistered(String key) {
        Map<String, Double> current = qpsMap;
        return current != null && current.containsKey(key);
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
