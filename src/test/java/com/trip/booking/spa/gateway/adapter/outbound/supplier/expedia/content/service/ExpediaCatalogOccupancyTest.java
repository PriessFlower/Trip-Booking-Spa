package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建档占用取自运行时配置，不得写死。
 *
 * <p>背景：本类原先写死 {@code occupancies=["1"]}（旧实现遗留）。occupancy 是 productKey
 * 的成分，而目录只按 {@code product_key} 精确相等取用
 * （{@code ProductCatalogMapper.selectAttributesByProductKeys} 的 {@code WHERE product_key IN}），
 * 所以没建的占用一律取不到。2026-08-28 生产开闸后同一张表长出两套互不相交的键：
 * <pre>
 *   occupancy=2  operator=expedia-refresh    1,459 行 / 73 家
 *   occupancy=1  operator=expedia-transform    193 行 / 55 家
 * </pre>
 *
 * <p><b>本测试不断言该建哪几个占用</b>——那是运营口径，本仓没有依据可证，写死任何具体值
 * 都是臆断。只钉住"必须可配、且不再写死"这一条：与 {@code elong-cps}/{@code fliggy-cps}
 * 同形，运维改 Nacos 即可，不必发版。
 */
class ExpediaCatalogOccupancyTest {

    /** 占用必须来自 Nacos 运行时键，而不是常量 */
    @Test
    void occupanciesComeFromRuntimeConfig() throws Exception {
        Field f = ExpediaProductMappingService.class.getDeclaredField("catalogOccupancies");
        Value v = f.getAnnotation(Value.class);

        assertNotNull(v, "建档占用必须由 @Value 绑定运行时配置——写死则改一次要发一次版");
        assertTrue(v.value().contains("task.expedia-catalog.occupancies"),
                "键名须为 task.expedia-catalog.occupancies（与 elong-cps/fliggy-cps 同形），实际=" + v.value());
        assertTrue(v.value().contains(":"),
                "必须带默认值，否则该键在 Nacos 缺席时启动即失败");
    }

    /** 不得再出现写死的占用常量——这是本次要根除的形态 */
    @Test
    void noHardcodedOccupancyConstantRemains() {
        for (Field f : ExpediaProductMappingService.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertTrue(!(n.contains("occupanc") && java.lang.reflect.Modifier.isStatic(f.getModifiers())),
                    "又出现了写死的占用常量 " + f.getName() + "：占用是运营口径，只能来自配置");
        }
    }
}
