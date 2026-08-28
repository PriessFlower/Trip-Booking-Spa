package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 刷价占用取自运行时配置。
 *
 * <p>占用既是 productKey 的成分，也是缓存键的一维——没刷的占用，该占用的查询如实拿空。
 * 本类原先写死 {@code List.of("2")}，改配置项一直被推迟（原注释：「改成配置项属行为变更，
 * 本次不动」）。艺龙侧同名方法早已可配，且其注释记着只刷单一占用导致的 2026-08-22
 * 曝光断粮事故。
 *
 * <p><b>不断言该刷哪几个占用</b>——那取决于渠道实际按几人问价，属运营口径，本仓无依据可证。
 * 只钉住：可配、能解析多值、键缺席时保持改动前的行为（"2"）。
 */
class ExpediaRefreshDimensionsTest {

    private static List<String> dimensionsWith(String configured) {
        ExpediaCPSQueryPriceServiceImpl svc = new ExpediaCPSQueryPriceServiceImpl();
        MockEnvironment env = new MockEnvironment();
        if (configured != null) {
            env.setProperty("task.expedia-cps.occupancies", configured);
        }
        ReflectionTestUtils.setField(svc, "environment", env);
        @SuppressWarnings("unchecked")
        List<String> dims = (List<String>) ReflectionTestUtils.invokeMethod(svc, "dimensions");
        return dims;
    }

    /** 键缺席时必须保持改为配置项之前的行为，否则这次改动会静默改变生产刷价口径 */
    @Test
    void fallsBackToPreviousHardcodedValue() {
        assertEquals(List.of("2"), dimensionsWith(null),
                "键缺席时应回到改动前的写死值 \"2\"——兜底变了等于偷偷改了生产行为");
    }

    /** 单值 */
    @Test
    void readsSingleOccupancy() {
        assertEquals(List.of("1"), dimensionsWith("1"));
    }

    /** 多值：每个占用各刷一遍，顺序保持配置里的书写顺序 */
    @Test
    void readsMultipleOccupanciesInOrder() {
        assertEquals(List.of("1", "2"), dimensionsWith("1,2"));
    }

    /** 空格与空项要容错——运维手写配置时很容易带上 */
    @Test
    void trimsBlanksAndSkipsEmptyEntries() {
        assertEquals(List.of("1", "2"), dimensionsWith(" 1 , ,2 "));
    }
}
