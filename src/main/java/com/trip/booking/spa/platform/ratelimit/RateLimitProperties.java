package com.trip.booking.spa.platform.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 限流配置（Nacos 一处配置，{@code @RefreshScope} 改配置热生效）。
 *
 * <pre>
 * ratelimit:
 *   default-qps: 1             # 未登记的键用这个。安全侧取小值——漏登记该被憋死，不该放飞
 *   acquire-timeout-ms: 5000   # 前台用途的等待上限
 *   qps:
 *     "[GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES]": 13
 *     "[GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH]": 10
 * </pre>
 *
 * <p><b>为什么从 JSON 字符串改成 YAML map</b>（2026-08-25）：原先 16 个键挤在一行 899 字符的
 * JSON 串里，三个后果都真实发生过——
 * <ol>
 *   <li>改一个配额只能对那一行 {@code sed}，而「同一接口的各用途桶之和 ≤ 接口桶」这条不变式
 *       肉眼完全看不出来；</li>
 *   <li>JSON 写错一个字符 → 整张表解析失败 → <b>所有键一起回落 default-qps</b>。原代码为此专门
 *       写了两层判空防御，注释里写着"等于整个网关瘫痪"。改成 YAML 后这个故障模式不存在了：
 *       写错一行只影响那一行，而且启动就报；</li>
 *   <li>example 与生产反复漂而无法 diff。</li>
 * </ol>
 *
 * <p><b>键仍然是扁平的</b>，这是刻意的：日志里打出来的就是这个字符串（{@code GLOBAL_LIMIT:供应商:
 * 接口:用途}），能直接拿去 grep 配置。改成嵌套树要在脑子里拼，反而更难对。
 *
 * <p><b>键必须用 {@code []} 包起来</b>：Spring 的宽松绑定会把裸 map 键里的冒号当分隔符<b>吃掉</b>——
 * {@code GLOBAL_LIMIT:ELONG:X} 会绑成 {@code GLOBAL_LIMITELONGX}，于是运行期按原键查表必然 miss、
 * 所有键静默回落 {@code default-qps}。2026-08-25 改格式时实测踩到：日志显示"已加载 5 个 key"
 * 一切正常，而限流器实际用的是兜底值。有 {@code RateLimitKeyBindingTest} 守着这条。
 */
@Slf4j
@Component
@RefreshScope
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** 未登记键的兜底。取小值：漏登记应表现为"被憋死"而不是"按 20 QPS 放飞"（§3.3.3 安全侧） */
    private double defaultQps = 1d;

    private int acquireTimeoutMs = 5000;

    /** 键 = BaseHttpAccess 拼出的 {@code GLOBAL_LIMIT:<供应商>:<接口>[:<用途>]} */
    private Map<String, Double> qps = new LinkedHashMap<>();

    /**
     * 过渡期兼容：旧格式是把整张表塞进一个 JSON 字符串（{@code qps: '{"KEY":13,...}'}）。
     *
     * <p><b>为什么必须两种都认</b>：直接切格式的两个方向都不安全——先改 Nacos 则旧代码读到空、
     * 所有键回落 {@code default-qps}（当时是 20，对艺龙即超速）；先发版则新代码把字符串绑到
     * {@code Map} 会失败、服务起不来。两format并存使「发版」与「改配置」解耦，各自可独立回滚。
     *
     * <p>Nacos 转成 YAML map 之后即可删除本字段与 {@link #legacyQpsJson}。
     */
    @org.springframework.beans.factory.annotation.Value("${ratelimit.qps:}")
    private transient String legacyQpsJson;

    public void setDefaultQps(double defaultQps) {
        this.defaultQps = defaultQps;
    }

    public void setAcquireTimeoutMs(int acquireTimeoutMs) {
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public void setQps(Map<String, Double> qps) {
        this.qps = qps == null ? Collections.emptyMap() : qps;
    }

    public Map<String, Double> getQps() {
        return qps;
    }

    @PostConstruct
    public void init() {
        if ((qps == null || qps.isEmpty()) && org.apache.commons.lang3.StringUtils.isNotBlank(legacyQpsJson)) {
            qps = parseLegacyJson(legacyQpsJson);
            log.warn("[gate] ratelimit.qps 仍是旧的 JSON 字符串格式，已按兼容路径解析出 {} 个 key。"
                    + "请尽快改成 YAML map（键须用 [] 包住），改完可删除本兼容分支", qps.size());
        }
        if (qps == null || qps.isEmpty()) {
            // 空表不是致命的（各键走 default-qps），但一定是配置事故——按安全侧的 default-qps=1
            // 跑起来会明显变慢，宁可让人立刻看见
            log.error("[gate] ratelimit.qps 为空，所有键将回落 default-qps={}。请检查 Nacos 配置", defaultQps);
            qps = Collections.emptyMap();
            return;
        }
        log.info("ratelimit.qps 已加载 {} 个 key，其余走 default-qps={}", qps.size(), defaultQps);
        checkBucketSums(qps);
    }

    /**
     * 解析旧格式：{@code {"KEY":13,"KEY2":10}}。只在过渡期用，故不追求严谨——
     * 解析不出来的条目跳过并落日志，而不是让整张表变空（那正是旧格式最糟的故障模式）。
     */
    private Map<String, Double> parseLegacyJson(String json) {
        Map<String, Double> parsed = new LinkedHashMap<>();
        for (String part : json.replace("{", "").replace("}", "").split(",")) {
            int colon = part.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = part.substring(0, colon).trim().replace("\"", "");
            String value = part.substring(colon + 1).trim().replace("\"", "");
            try {
                if (!key.isEmpty()) {
                    parsed.put(key, Double.parseDouble(value));
                }
            } catch (NumberFormatException e) {
                log.error("[gate] 旧格式 ratelimit.qps 里这一项解析不出数值，已跳过: {}", part);
            }
        }
        return parsed;
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
        Map<String, Double> current = qps;
        return current != null && current.containsKey(key);
    }

    public int getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    /**
     * 查某个限流 key 的 QPS，未配置则用全局默认。
     *
     * <p>此处判空是有意的冗余：本方法在所有供应商调用的必经路径上，一旦抛错即全站不可用。
     */
    public double qpsOf(String key) {
        Map<String, Double> current = qps;
        return current == null ? defaultQps : current.getOrDefault(key, defaultQps);
    }
}
