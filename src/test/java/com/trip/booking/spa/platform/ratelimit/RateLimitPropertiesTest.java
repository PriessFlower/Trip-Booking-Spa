package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住限流配置解析的兜底。
 *
 * <p>本类覆盖的是一个已发生的缺陷：{@code JsonUtils.decodeJson} 解析失败时吞掉异常返回
 * null，而 {@code init()} 只写了 catch，于是 qpsMap 被赋成 null，此后每次 qpsOf 都空指针。
 * 而 qpsOf 在所有供应商调用的必经路径上——运维在 Nacos 写错一个字符即可让整个网关瘫痪，
 * 且报错是空指针，完全指不向配置。
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

        assertEquals(10d, p.qpsOf(KEY));
    }

    /**
     * 非法 JSON 必须回落默认而不是抛错——这正是缺陷所在。
     * 修复前此处会因 qpsMap 为 null 而空指针。
     */
    @Test
    void fallsBackToDefaultWhenJsonIsMalformed() {
        RateLimitProperties p = propertiesWith("{这不是合法 JSON");

        assertDoesNotThrow(() -> p.qpsOf(KEY));
        assertEquals(10d, p.qpsOf(KEY));
    }

    /** JSON 合法但类型不符（值不是数字）同样不得抛错 */
    @Test
    void fallsBackToDefaultWhenValueTypeIsWrong() {
        RateLimitProperties p = propertiesWith("{\"" + KEY + "\":\"不是数字\"}");

        assertDoesNotThrow(() -> p.qpsOf(KEY));
    }

    /** 未配置该项时回落默认 */
    @Test
    void fallsBackToDefaultWhenJsonIsBlank() {
        RateLimitProperties p = propertiesWith("");

        assertEquals(10d, p.qpsOf(KEY));
    }

    /**
     * qpsOf 自身的判空冗余：即便 qpsMap 被置空，也不得抛错。
     * 该方法在所有供应商调用的必经路径上，抛错即全站不可用。
     */
    @Test
    void neverThrowsEvenIfMapIsNull() {
        RateLimitProperties p = propertiesWith("{}");
        ReflectionTestUtils.setField(p, "qpsMap", null);

        assertDoesNotThrow(() -> p.qpsOf(KEY));
        assertEquals(10d, p.qpsOf(KEY));
    }

    private RateLimitProperties propertiesWith(String qpsJson) {
        RateLimitProperties p = new RateLimitProperties();
        ReflectionTestUtils.setField(p, "qpsJson", qpsJson);
        ReflectionTestUtils.setField(p, "defaultQps", 10d);
        p.init();
        return p;
    }
}
