package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住两级桶的分配语义：接口桶是对供应商的承诺，用途桶是我方内部怎么分。
 *
 * <p>驱动数据取<b>生产 Nacos 当下的真实取值</b>（2026-08-24 从 app 的配置快照读出，
 * platform 那台 172.21.32.14 / namespace=prod），而不是现造的样例——这些数正是要被
 * 改动影响的对象，用假数据测等于没测。
 */
class PurposeBucketAllocationTest {

    /** 生产实际取值（2026-08-24 读自 app 的 Nacos 配置快照），只截取艺龙相关的键 */
    private static final String PROD_ELONG =
            "{\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES\":6,"
                    + "\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH\":5,"
                    + "\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_ORDER_PRICE\":2,"
                    + "\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_CREATE_ORDER\":1,"
                    + "\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_QUERY_ORDER\":1,"
                    + "\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_CANCEL_ORDER\":1}";

    private static final String DETAIL = "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES";

    @Test
    @DisplayName("未登记的用途桶必须报「未登记」，不得回落 default-qps")
    void unregisteredPurposeBucketIsNotSilentlyGivenTheDefault() {
        RateLimitProperties p = propertiesWith(PROD_ELONG);

        // 生产此刻只登记了 :REFRESH，另两路还没有
        assertTrue(p.isRegistered(DETAIL + ":REFRESH"));
        assertFalse(p.isRegistered(DETAIL + ":CHECK_PRICE"));
        assertFalse(p.isRegistered(DETAIL + ":LIVE"));

        // 而 qpsOf 对未登记的键回落 default-qps（生产 20）——比接口桶还大。
        // 这正是不能拿 qpsOf 判"有没有配"的原因：忘配会表现成"配了个很大的额度"
        assertEquals(20d, p.qpsOf(DETAIL + ":CHECK_PRICE"));
        assertEquals(6d, p.qpsOf(DETAIL));
    }

    @Test
    @DisplayName("代码可先于配置发布：新键未进 Nacos 时行为与改动前一致")
    void codeMayShipBeforeConfig() {
        RateLimitProperties p = propertiesWith(PROD_ELONG);

        // 通道层的判据就是 isRegistered：两个新用途桶未登记 → 只扣接口桶 →
        // 与改动前（只有接口桶 + 刷价手写一次子桶）的效果一致，不会因为发版而变速
        assertFalse(p.isRegistered(DETAIL + ":CHECK_PRICE"));
        assertFalse(p.isRegistered(DETAIL + ":LIVE"));
        assertTrue(p.isRegistered(DETAIL));
    }

    @Test
    @DisplayName("按生产现值补齐三路用途桶会越界——发版时必须同步下调 REFRESH")
    void addingTheTwoNewBucketsAtProdValuesWouldBreachTheInterfaceBucket() {
        // 生产现值：接口桶 6、REFRESH 5。若照 example 的 CHECK_PRICE=1 / LIVE=1 直接补齐，
        // 三路之和 = 7 > 6，即"内部分配之和超过对艺龙的承诺"。
        // 这条测试的作用是把这个部署顺序钉下来：补新键的同一次改动里必须把 REFRESH 降到 4。
        double iface = 6d;
        double refreshNow = 5d;
        double checkPrice = 1d;
        double live = 1d;

        assertTrue(refreshNow + checkPrice + live > iface,
                "前提变了：若生产的接口桶或 REFRESH 已调整，本测试记录的部署注意事项需重写");
        assertTrue(4d + checkPrice + live <= iface,
                "REFRESH 降到 4 后三路之和应当落回接口桶之内");
    }

    @Test
    @DisplayName("越界只告警不改值：配置的事在配置里修")
    void breachIsReportedButValuesAreLeftAlone() {
        String breaching = "{\"" + DETAIL + "\":6,"
                + "\"" + DETAIL + ":REFRESH\":5,"
                + "\"" + DETAIL + ":CHECK_PRICE\":1,"
                + "\"" + DETAIL + ":LIVE\":1}";

        RateLimitProperties p = propertiesWith(breaching);

        // 加载时会打 [gate] 越界错误日志（见 checkBucketSums），但取值原样保留——
        // 运行期偷偷改数只会让人对着 Nacos 猜为什么不生效
        assertEquals(5d, p.qpsOf(DETAIL + ":REFRESH"));
        assertEquals(6d, p.qpsOf(DETAIL));
    }

    @Test
    @DisplayName("接口键自身不得被误当成某个键的用途桶")
    void interfaceKeyIsNotMistakenForAPurposeBucket() {
        // 接口键截掉最后一段是 GLOBAL_LIMIT:ELONG，它不在表里，故不参与任何求和。
        // 若这里判错，所有接口桶都会被算成"GLOBAL_LIMIT:ELONG"的子桶而误报越界
        RateLimitProperties p = propertiesWith(PROD_ELONG);

        assertFalse(p.isRegistered("GLOBAL_LIMIT:ELONG"));
        assertEquals(2d, p.qpsOf("GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_ORDER_PRICE"));
    }

    private RateLimitProperties propertiesWith(String qpsJson) {
        RateLimitProperties p = new RateLimitProperties();
        p.setQps(asQpsMap(qpsJson));
        ReflectionTestUtils.setField(p, "defaultQps", 20d);
        ReflectionTestUtils.setField(p, "acquireTimeoutMs", 5000);
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
