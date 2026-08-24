package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
 * <p>2026-08-24 起取许可的动作也从本类移走：刷价只声明用途 {@code CallPurpose.REFRESH}，
 * 扣格由通道层做。故本测试改为断言"业务代码里没有限流动作"，而不是"业务代码里有 acquire"——
 * 手写 acquire 恰恰是要消除的形态：用途桶不是接口维度的键，没有任何机制保证新增的
 * hotel.detail 调用路径会记得写那一行，忘写即静默绕过分配。
 *
 * <p>并发度（{@code high-concurrency}）不在禁止范围：它是线程池容量，不是速率。速率决定
 * 打供应商多快，并发度决定用几个线程去等许可，两者语义不同、上限来源也不同（前者是供应商
 * 配额，后者是连接池）。
 */
class ElongRefreshRateLimitOwnershipTest {

    private static final Path SERVICE = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
            + "/outbound/supplier/elong/pricing/ElongCPSQueryPriceServiceImpl.java");
    private static final Path CHANNEL = Path.of("src/main/java/com/trip/booking/spa/platform"
            + "/http/BaseHttpAccess.java");
    private static final Path PERMITS = Path.of("src/main/java/com/trip/booking/spa/platform"
            + "/ratelimit/Permits.java");
    private static final Path NACOS_EXAMPLE = Path.of("config/nacos/trip-booking-spa.yaml.example");

    @Test
    @DisplayName("刷价不得自建限流器")
    void mustNotBuildItsOwnLimiter() throws Exception {
        String src = Files.readString(SERVICE);
        assertFalse(src.contains("RateLimiter.create"),
                "刷价又自建了 Guava 限流器。限流一律走统一限流，速率配在 Nacos 的 ratelimit.qps（§3.3）");
    }

    @Test
    @DisplayName("取许可的动作只许在通道层，业务代码不得手写")
    void permitTakenInChannelOnly() throws Exception {
        String src = Files.readString(SERVICE);
        assertFalse(src.contains("RateLimitHolder.get().acquire(")
                        || src.contains("RateLimitHolder.get().tryAcquire("),
                "刷价又在业务代码里手写取许可了。用途桶不是接口维度的键——手写就意味着新增的 "
                        + "hotel.detail 调用路径可以忘记写那一行，忘写即静默绕过分配。"
                        + "正确形态是声明 CallPurpose，由通道层扣格");
    }

    @Test
    @DisplayName("两级扣格的规则只许有一份实现（Permits）")
    void bucketRuleLivesInOnePlace() throws Exception {
        String src = Files.readString(PERMITS);
        assertTrue(src.contains("purpose.name()"),
                "没有按用途拼子桶键。键结构是 GLOBAL_LIMIT:<供应商>:<接口>[:<用途>]，"
                        + "用途段由 CallPurpose 提供");
        assertTrue(src.contains("isRegistered("),
                "用途桶必须先判是否登记：未登记就只扣接口桶。不能用 qpsOf 判断——"
                        + "它对未登记的键回落 default-qps，等于给忘配的子桶发一个很大的额度");
        assertTrue(src.contains("purpose.failFast()"),
                "等还是走必须由用途决定。此前是「刷价在业务代码里阻塞、其余在通道层非阻塞」，"
                        + "同一件事分散两层，读代码的人得两处都读到才知道自己会不会被挂住");
        assertTrue(Files.readString(CHANNEL).contains("Permits.take("),
                "通道层必须走 Permits 取许可，不得自己再实现一遍两级规则");
    }

    @Test
    @DisplayName("取许可只许经 Permits——不得有第二条路直接碰限流中枢")
    void nobodyBypassesPermits() throws Exception {
        List<Path> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.endsWith("Permits.java") || p.endsWith("RateLimitHolder.java")) {
                    continue;
                }
                String s = Files.readString(p);
                if (s.contains("RateLimitHolder.get().acquire(")
                        || s.contains("RateLimitHolder.get().tryAcquire(")) {
                    offenders.add(p);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "这些地方绕过 Permits 直接取许可，两级桶的分配对它们不成立：" + offenders
                        + "。历史上正是这样漏掉三条路（大文件下载、Expedia 两个静态内容客户端）——"
                        + "分配一旦有例外就退化成建议值");
    }

    @Test
    @DisplayName("速率键不得回到 task 域，只许留在 ratelimit.qps")
    void rateMustNotLiveInTaskDomain() throws Exception {
        String yaml = Files.readString(NACOS_EXAMPLE);
        Matcher m = Pattern.compile("^\\s*(high|normal|deal|far)-qps\\s*:", Pattern.MULTILINE).matcher(yaml);
        assertFalse(m.find(),
                "task.elong-cps 下又出现了 *-qps。刷价速率是统一限流的一个用途桶，"
                        + "取值只许在 ratelimit.qps，否则运维会对着两个开关猜哪个生效");
    }

    @Test
    @DisplayName("hotel.detail 的三路用途桶必须都登记，且之和不超过接口桶")
    void allPurposeBucketsRegisteredAndWithinInterface() throws Exception {
        String yaml = Files.readString(NACOS_EXAMPLE);
        String iface = "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES";
        double total = quotaOf(yaml, iface);
        double refresh = quotaOf(yaml, iface + ":REFRESH");
        double checkPrice = quotaOf(yaml, iface + ":CHECK_PRICE");
        double live = quotaOf(yaml, iface + ":LIVE");

        assertTrue(total > 0, "接口桶未登记，各路会一起回落 default-qps");
        assertTrue(refresh > 0, "刷价用途桶未登记。未登记时通道层只扣接口桶，"
                + "刷价会按接口桶跑满、不给客流留头");
        assertTrue(checkPrice > 0, "验价用途桶未登记。hotel.detail 是刷价与「点订前现取现验」"
                + "共用的接口，客流那一路没有自己的桶，「留头」就只是算术约定而不是机制");
        assertTrue(live > 0, "实时查价用途桶未登记。它量很小，但没有桶就意味着有一条路"
                + "可以只吃接口桶——分配一旦有例外就退化成建议值");

        double sum = refresh + checkPrice + live;
        assertTrue(sum <= total,
                String.format("三路用途桶之和 %.1f 超过接口桶 %.1f。接口桶是对艺龙的承诺，"
                        + "用途桶是我方内部分配，之和越界即承诺可能被打破", sum, total));
    }

    /** 从 ratelimit.qps 那行 JSON 里取某个键的配额；取不到返回 0 */
    private static double quotaOf(String yaml, String key) {
        // 用途桶键是接口键加后缀，正则须锚定右引号，否则查接口键会先命中用途桶
        Matcher m = Pattern.compile(Pattern.quote("\"" + key + "\"") + "\\s*:\\s*([0-9.]+)").matcher(yaml);
        return m.find() ? Double.parseDouble(m.group(1)) : 0d;
    }
}
