package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 2026-08-25 踩到的一个<b>静默失效</b>：Spring 的宽松绑定会把裸 map 键里的冒号当分隔符吃掉。
 *
 * <p>限流键形如 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH}。把
 * {@code ratelimit.qps} 从 JSON 串改成 YAML map 时，键若不用 {@code []} 包起来，会被绑成
 * {@code GLOBAL_LIMITELONGSPA_SUPPLIER_API_PRODUCT_PRICESREFRESH}——**冒号消失**。
 *
 * <p>后果是最坏的那种：启动日志照样打"ratelimit.qps 已加载 5 个 key"，一切看起来正常；而运行期
 * 按原键查表全部 miss，所有配额静默回落 {@code default-qps}。当时是靠"限流器写进 Redis 的窗口
 * 是 1000ms 而不是期望的 77ms"才发现的——**任何只看键数量的检查都会漏掉它**。
 *
 * <p>故本测试同时守两头：绑定行为（括号语法必须保原样、裸键必须被吃掉——后者是为了让这条测试
 * 在 Spring 行为变化时能被察觉），以及配置示例里必须真的用了括号。
 */
class RateLimitKeyBindingTest {

    private static final String KEY = "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES";
    private static final String SUB = KEY + ":REFRESH";

    @Test
    @DisplayName("括号语法必须原样保留带冒号的键")
    void bracketNotationPreservesColons() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("ratelimit.qps[" + KEY + "]", 13);
        src.put("ratelimit.qps[" + SUB + "]", 10);

        Map<String, Double> bound = bind(src);

        assertEquals(2, bound.size());
        assertEquals(13d, bound.get(KEY),
                "括号语法下键被改写了。运行期按 " + KEY + " 查表会 miss，"
                        + "所有配额静默回落 default-qps——而启动日志看起来完全正常");
        assertEquals(10d, bound.get(SUB));
    }

    @Test
    @DisplayName("裸键会被吃掉冒号——这是当时的病因，钉住它以便 Spring 行为变化时能察觉")
    void bareKeysLoseTheirColons() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("ratelimit.qps." + KEY, 13);

        Map<String, Double> bound = bind(src);

        assertFalse(bound.containsKey(KEY),
                "裸键这次保住了冒号——Spring 的宽松绑定行为变了。这是好消息，"
                        + "但配置格式与本测试都该重新评估，别让「括号是必须的」这条过期成迷信");
        assertTrue(bound.containsKey("GLOBAL_LIMITELONGSPA_SUPPLIER_API_PRODUCT_PRICES"),
                "裸键的绑定结果既不是原键也不是已知的吃冒号结果，实际是：" + bound.keySet());
    }

    @Test
    @DisplayName("配置示例里的限流键必须都用括号包着")
    void exampleUsesBrackets() throws Exception {
        String yaml = Files.readString(Path.of("config/nacos/trip-booking-spa.yaml.example"));
        Matcher m = Pattern.compile("^\\s+\"([^\"]*GLOBAL_LIMIT[^\"]*)\"\\s*:", Pattern.MULTILINE).matcher(yaml);
        int checked = 0;
        while (m.find()) {
            String key = m.group(1);
            checked++;
            assertTrue(key.startsWith("[") && key.endsWith("]"),
                    "配置示例里的键 \"" + key + "\" 没用 [] 包起来，绑定时冒号会被吃掉");
        }
        assertTrue(checked >= 10, "只扫到 " + checked + " 个限流键，示例可能已改格式，本测试需同步");
    }

    @Test
    @DisplayName("过渡期必须两种格式都认——否则「发版」与「改配置」无法解耦")
    void legacyJsonStringStillParses() {
        RateLimitProperties p = new RateLimitProperties();
        org.springframework.test.util.ReflectionTestUtils.setField(p, "legacyQpsJson",
                "{\"" + KEY + "\":13,\"" + SUB + "\":10}");
        p.init();

        assertEquals(13d, p.qpsOf(KEY),
                "旧 JSON 格式解析不出来。直接切格式的两个方向都不安全：先改 Nacos 则旧代码读到空、"
                        + "所有键回落 default-qps；先发版则新代码把字符串绑到 Map 会失败、服务起不来");
        assertTrue(p.isRegistered(SUB), "用途桶也应从旧格式里认出来，否则会被当成未登记");
    }

    @Test
    @DisplayName("新格式优先：两者都在时不吃旧的")
    void mapWinsOverLegacyJson() {
        RateLimitProperties p = new RateLimitProperties();
        p.setQps(Map.of(KEY, 20d));
        org.springframework.test.util.ReflectionTestUtils.setField(p, "legacyQpsJson",
                "{\"" + KEY + "\":13}");
        p.init();

        assertEquals(20d, p.qpsOf(KEY), "已有 YAML map 时不该再吃旧 JSON——否则迁移完还会被旧值盖回去");
    }

    private static Map<String, Double> bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("ratelimit.qps", Bindable.mapOf(String.class, Double.class))
                .orElse(Map.of());
    }
}
