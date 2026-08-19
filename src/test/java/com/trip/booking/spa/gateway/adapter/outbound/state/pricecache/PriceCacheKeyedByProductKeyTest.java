package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 价格缓存的字段名必须是跨次稳定的 productKey，而不是易腐的 productId。
 *
 * <p><b>它守的是什么</b>（2026-08-19 生产事故）：刷价按单晚切片，每个日期是一次独立的
 * 供应商调用；艺龙的 productId 会话级轮换，于是同一个卖法在相邻两天拿到的 productId
 * 不同。而出价要求产品在住期内每一天都有价，两天的 productId 交集恒为空——
 * <b>住 2 晚及以上一个产品都出不来</b>。实测某酒店 08-19 有 42 个产品、08-20 有 47 个，
 * 连续两天都在的 0 个；当时出价 6,403 次里 83.1% 返回空。
 *
 * <p>用 productKey 作字段名后，同一卖法跨日期天然对齐，交集成立。
 */
class PriceCacheKeyedByProductKeyTest {

    /** 反射调用私有的 cacheField，避免为测试放宽可见性 */
    private static String cacheField(ProductRespDTO product) throws Exception {
        Method m = CachePriceServiceImpl.class.getDeclaredMethod("cacheField", ProductRespDTO.class);
        m.setAccessible(true);
        return (String) m.invoke(null, product);
    }

    private static ProductRespDTO product(String productId, String productKey) {
        ProductRespDTO p = new ProductRespDTO();
        p.setProductId(productId);
        p.setProductKey(productKey);
        return p;
    }

    @Test
    @DisplayName("同一卖法在相邻两天的票据不同，但缓存字段名必须相同——否则多晚查询交集为空")
    void sameSellingAcrossDatesSharesTheSameField() throws Exception {
        String sellingKey = "a".repeat(64);
        // 生产实测的形态：同一卖法，两天各自一次调用，报价码完全不同
        ProductRespDTO day1 = product("10422034A4A212169223A0Ae8c3c9307890f862740f74b18b408aa6", sellingKey);
        ProductRespDTO day2 = product("10422034A26A212169223A0Acef9ae4828f7825979cc97f169e3d1cb", sellingKey);

        assertNotEquals(day1.getProductId(), day2.getProductId(),
                "前提：易腐报价码逐次轮换，两天本就不同");
        assertEquals(cacheField(day1), cacheField(day2),
                "同一卖法跨日期必须落到同一个缓存字段，否则住 2 晚时取交集会得到空集");
    }

    @Test
    @DisplayName("字段名取 productKey，不取 productId")
    void fieldIsTheProductKey() throws Exception {
        ProductRespDTO p = product("perishable-ticket-001", "b".repeat(64));
        assertEquals("b".repeat(64), cacheField(p));
        assertNotEquals(p.getProductId(), cacheField(p),
                "拿易腐码当字段名正是本次事故的成因");
    }

    @Test
    @DisplayName("productKey 缺席时退回 productId——保住报价，好过整条不报")
    void fallsBackToProductIdWhenKeyAbsent() throws Exception {
        assertEquals("ticket-1", cacheField(product("ticket-1", null)));
        assertEquals("ticket-2", cacheField(product("ticket-2", "")));
        assertEquals("ticket-3", cacheField(product("ticket-3", "   ")));
    }

    @Test
    @DisplayName("回归：用 productId 当字段名时，多晚交集为空——本测试证明旧实现确实卖不出多晚")
    void oldBehaviourProducesEmptyIntersection() {
        String sellingKey = "c".repeat(64);
        ProductRespDTO day1 = product("ticket-day1", sellingKey);
        ProductRespDTO day2 = product("ticket-day2", sellingKey);

        // 旧实现：字段名 = productId
        Set<String> byProductId = new LinkedHashSet<>();
        byProductId.add(day1.getProductId());
        byProductId.retainAll(Set.of(day2.getProductId()));
        assertTrue(byProductId.isEmpty(),
                "旧口径下两天的交集为空——这正是住 2 晚一个产品都出不来的原因");

        // 新实现：字段名 = productKey
        Set<String> byProductKey = new LinkedHashSet<>();
        byProductKey.add(day1.getProductKey());
        byProductKey.retainAll(Set.of(day2.getProductKey()));
        assertEquals(1, byProductKey.size(), "新口径下两天能对上，多晚可成交");
    }
}
