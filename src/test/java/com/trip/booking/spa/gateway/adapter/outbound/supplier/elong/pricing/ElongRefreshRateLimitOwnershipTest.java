package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫 §3.3：限流<b>只许</b>走统一限流，速率取值只许在 Nacos。
 *
 * <p>为什么要一条测试盯着：刷价曾经在本类里 {@code RateLimiter.create()} 自建了一个 Guava
 * 限流器，速率另配在 {@code task.elong-cps.high-qps}。后果不是"多写了几行"——是同一件事有了
 * 两个开关、两处取值，运维照规范去改统一限流却不生效，而真正生效的那个藏在业务代码里。
 * 2026-08-19 收口后加此测试，防止下一次"图省事"重演。
 *
 * <p>并发度（{@code high-concurrency}）不在禁止范围：它是线程池容量，不是速率。速率决定
 * 打供应商多快，并发度决定用几个线程去等许可，两者语义不同、上限来源也不同（前者是供应商
 * 配额，后者是连接池）。
 */
class ElongRefreshRateLimitOwnershipTest {

    private static final Path SERVICE = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
            + "/outbound/supplier/elong/pricing/ElongCPSQueryPriceServiceImpl.java");
    private static final Path NACOS_EXAMPLE = Path.of("config/nacos/trip-booking-spa.yaml.example");

    @Test
    @DisplayName("刷价不得自建限流器，必须走统一限流")
    void mustNotBuildItsOwnLimiter() throws Exception {
        String src = Files.readString(SERVICE);
        assertFalse(src.contains("RateLimiter.create"),
                "刷价又自建了 Guava 限流器。限流一律走 RateLimitHolder.get().acquire(key)，"
                        + "速率配在 Nacos 的 ratelimit.qps（§3.3）");
        assertTrue(src.contains("RateLimitHolder.get().acquire("),
                "刷价必须通过统一限流取许可——阻塞版 acquire 才有排队语义，"
                        + "tryAcquire 超限会把该行变成假失败、价格静默不刷");
    }

    @Test
    @DisplayName("速率键不得回到 task 域，只许留在 ratelimit.qps")
    void rateMustNotLiveInTaskDomain() throws Exception {
        String yaml = Files.readString(NACOS_EXAMPLE);
        Matcher m = Pattern.compile("^\\s*(high|normal)-qps\\s*:", Pattern.MULTILINE).matcher(yaml);
        assertFalse(m.find(),
                "task.elong-cps 下又出现了 *-qps。刷价速率是统一限流的子配额，"
                        + "取值只许在 ratelimit.qps，否则运维会对着两个开关猜哪个生效");
    }

    @Test
    @DisplayName("刷价子配额必须已登记，且严格小于接口总额")
    void refreshSubQuotaRegisteredAndBelowTotal() throws Exception {
        String yaml = Files.readString(NACOS_EXAMPLE);
        double total = quotaOf(yaml, "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES");
        double refresh = quotaOf(yaml, "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES:REFRESH");

        assertTrue(refresh > 0, "刷价子配额未登记。未登记时 qpsOf 回落 default-qps（生产 20），"
                + "刷价会瞬间把艺龙账号的 10 QPS 硬额度烧穿");
        assertTrue(refresh < total,
                String.format("刷价子配额 %.1f 必须严格小于查价接口总额 %.1f——那 5 个额度与验价共用，"
                        + "刷价吃干会让客人正在点的验价卡在 acquire 超时", refresh, total));
    }

    /** 从 ratelimit.qps 那行 JSON 里取某个键的配额；取不到返回 0 */
    private static double quotaOf(String yaml, String key) {
        // 子配额键是父键加后缀，正则须锚定右引号，否则查父键会先命中子键
        Matcher m = Pattern.compile(Pattern.quote("\"" + key + "\"") + "\\s*:\\s*([0-9.]+)").matcher(yaml);
        return m.find() ? Double.parseDouble(m.group(1)) : 0d;
    }
}
