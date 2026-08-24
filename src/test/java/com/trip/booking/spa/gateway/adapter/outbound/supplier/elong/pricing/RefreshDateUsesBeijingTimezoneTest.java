package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住刷价的日期口径：换算入住日期时的"今天"必须是<b>北京的今天</b>，且不得依赖 JVM 默认时区。
 *
 * <p>缺陷现场（2026-08-25 01:00 生产实测）：容器未设 {@code TZ}，JVM 走 UTC，于是
 * {@code LocalDate.now()} 拿到 08-24 而北京已是 08-25。后果两头都错——
 *
 * <ul>
 *   <li>我们刷 08-24/25/26，其中 08-24 在艺龙口径里已经过去：当日无货率 56%，而次日 87%、
 *       第三日 90%。约三分之一的额度打在一个已经不存在的日期上；</li>
 *   <li>上游 cursor 启动参数写死 {@code -Duser.timezone=Asia/Shanghai}，它要的第三天（08-27）
 *       落在我们窗口之外，问过来只能拿到"未能确认"。</li>
 * </ul>
 *
 * <p><b>本测试断言代码，不断言环境。</b>第一版写成"比较 JVM 今天与北京今天"，在 EDT 的开发机上
 * 靠 {@code TZ=Asia/Shanghai} 才绿，CI 跑在 UTC 就会红——那是在测运行环境，不是测代码。正确的
 * 不变式是：<b>刷价路径不得出现无参的 {@code LocalDate.now()}</b>，时区必须显式给出。
 */
class RefreshDateUsesBeijingTimezoneTest {

    private static final Path REFRESH = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
            + "/outbound/supplier/elong/pricing/ElongCPSQueryPriceServiceImpl.java");
    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @Test
    @DisplayName("刷价换算日期必须显式给时区，不得用 JVM 默认")
    void refreshDateCarriesAnExplicitZone() throws Exception {
        String src = Files.readString(REFRESH);
        String code = stripComments(src);

        assertFalse(code.contains("LocalDate.now()"),
                "刷价里又出现了无参的 LocalDate.now()。它取 JVM 默认时区，而默认时区随基础镜像变——"
                        + "实测走 UTC 时整个日期窗口错位一天：三分之一额度打在已经过去的日期上，"
                        + "上游要的第三天反而没有。必须写成 LocalDate.now(SUPPLIER_ZONE)");

        assertTrue(code.contains("ZoneId.of(\"Asia/Shanghai\")"),
                "找不到显式的 Asia/Shanghai。日期口径是供应商属性——艺龙与上游 cursor 都按北京时间");

        assertTrue(Pattern.compile("LocalDate\\.now\\(SUPPLIER_ZONE\\)").matcher(code).find(),
                "换算入住/离店日期时没用上那个时区常量");
    }

    /**
     * 去掉注释再断言。
     *
     * <p>本测试第一版直接在原文里搜 {@code LocalDate.now()}，结果命中的是被测类 javadoc 里
     * 那句"用 {@code LocalDate.now()} 意味着…"——注释在解释这个坑，反而把测试自己绊倒了。
     * 断言的对象是代码，就不该让注释参与。
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("Dockerfile 仍钉着时区（日志与其他日期用途的兜底）")
    void dockerfileStillPinsTimezone() throws Exception {
        String df = Files.readString(DOCKERFILE);
        assertTrue(Pattern.compile("^ENV\\s+TZ=Asia/Shanghai\\s*$", Pattern.MULTILINE).matcher(df).find(),
                "Dockerfile 里的 ENV TZ=Asia/Shanghai 不见了。刷价本身已由 SUPPLIER_ZONE 兜住，"
                        + "但日志时间戳、其他 LocalDate.now() 调用点仍跟随容器时区——"
                        + "排障时日志时间与上游对不上，比算错日期更难查");
    }
}
