package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住限流配置解析的兜底。
 *
 * <p>本类原先覆盖的是「JSON 解析失败 → 整张表变空 → 所有键回落」这个故障模式。2026-08-25
 * 配置从 JSON 串改成 YAML map 之后，那个故障模式在结构上不存在了：写错一行只影响那一行，
 * 且启动即报。相应的两个用例已删除——留着会让人以为还有那条路。
 *
 * <p>剩下的用例守的是仍然成立的部分：未登记的键回落默认值、空表不抛错。
 * 键名绑定那个更隐蔽的坑由 {@code RateLimitKeyBindingTest} 守。
 */
class RateLimitPropertiesTest {

    private static final String KEY = "GLOBAL_LIMIT:EXPEDIA:SPA_SUPPLIER_API_PRODUCT_PRICES";

    /** 配置合法时按配置取值 */
    @Test
    void usesConfiguredQpsWhenJsonIsValid() {
        RateLimitProperties p = propertiesWith("{\"" + KEY + "\":50}");

        assertEquals(50d, p.qpsOf(KEY));
    }

    /** 配置合法但未列该 key 时回落全局默认 */
    @Test
    void fallsBackToDefaultForUnlistedKey() {
        RateLimitProperties p = propertiesWith("{\"OTHER_KEY\":50}");

        assertEquals(1d, p.qpsOf(KEY));
    }



    /** 未配置该项时回落默认 */
    @Test
    void fallsBackToDefaultWhenJsonIsBlank() {
        RateLimitProperties p = propertiesWith("");

        assertEquals(1d, p.qpsOf(KEY));
    }

    /**
     * qpsOf 自身的判空冗余：即便 qpsMap 被置空，也不得抛错。
     * 该方法在所有供应商调用的必经路径上，抛错即全站不可用。
     */
    @Test
    void neverThrowsEvenIfMapIsNull() {
        RateLimitProperties p = propertiesWith("{}");
        // 字段名随格式改动从 qpsMap 变成 qps；置 null 模拟绑定异常或并发下的中间态
        ReflectionTestUtils.setField(p, "qps", null);

        assertDoesNotThrow(() -> p.qpsOf(KEY));
        assertEquals(1d, p.qpsOf(KEY), "default-qps 的兜底值已从 10 改为安全侧的 1");
    }

    private RateLimitProperties propertiesWith(String qpsJson) {
        RateLimitProperties p = new RateLimitProperties();
        p.setQps(asQpsMap(qpsJson));
        ReflectionTestUtils.setField(p, "defaultQps", 1d);
        p.init();
        return p;
    }

    /** 把用例里写的 JSON 字面量转成配置 map。配置已改 YAML，但用例用 JSON 字面量更紧凑 */
    private static java.util.Map<String, Double> asQpsMap(String json) {
        java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
        for (String part : json.replace("{", "").replace("}", "").split(",")) {
            int colon = part.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = part.substring(0, colon).trim().replace("\"", "");
            String val = part.substring(colon + 1).trim().replace("\"", "");
            if (!key.isEmpty() && !val.isEmpty()) {
                m.put(key, Double.parseDouble(val));
            }
        }
        return m;
    }

}
