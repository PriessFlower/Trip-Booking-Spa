package com.trip.booking.spa.gateway.application.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷价车道的时区约束：<b>算"今天"必须显式给时区，不许用部署环境的默认值</b>。
 *
 * <p>为什么要成文（PROJECT.md §0.3：不成文就确实会被违反）——它已经被违反过。当时四家刷价里
 * 艺龙、飞猪、道旅都覆写了 {@code supplierZone()} 显式钉 Asia/Shanghai，唯独 Expedia 一直用
 * {@code LocalDate.now()}（JVM 默认时区），且同类里的跨天判定另用 {@code ZoneId.systemDefault()}。
 *
 * <p>它当时<b>没有出错</b>：2026-09-14 实测生产容器 {@code TZ=Asia/Shanghai}，两者同值。
 * 这恰恰是它危险的地方——正确性系于一个环境变量，改了不报错、不告警，只会让该家的刷价
 * 住期窗口整体平移一天，而另三家纹丝不动。这种偏差在日志里看不出来，在单测里也看不出来
 * （本机时区通常也是 Asia/Shanghai）。
 *
 * <p>基准取北京时间的理由是我方客源口径：上游按北京日期问价，刷价就该按同一个"今天"铺住期。
 */
class RefreshTimeZoneArchRulesTest {

    private static final Path SUPPLIER_ROOT =
            Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");

    /** 各家刷价实现。新接一家会多一个文件，本测试自动把它纳入约束 */
    private static List<Path> refreshImpls() {
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            return files.filter(p -> p.getFileName().toString().endsWith("CPSQueryPriceServiceImpl.java"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 剥掉注释再扫——约束的是<b>代码</b>怎么写，不是注释里能不能提这个词。
     * 首版没剥，于是「此前这里是 LocalDate.now()」这句解释性注释自己把测试打红了：
     * 那会逼着后来的人为了让测试过而删掉解释，正好删掉最该留的那部分。
     */
    private static String codeOf(Path p) {
        return read(p)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("刷价实现里禁止出现隐式时区：LocalDate.now() 无参、ZoneId.systemDefault()")
    void refreshMustNotUseAmbientTimeZone() {
        List<String> violations = new ArrayList<>();
        for (Path impl : refreshImpls()) {
            String source = codeOf(impl);
            if (source.contains("LocalDate.now()")) {
                violations.add(impl.getFileName() + " -> LocalDate.now()（无参=JVM 默认时区）");
            }
            if (source.contains("ZoneId.systemDefault()")) {
                violations.add(impl.getFileName() + " -> ZoneId.systemDefault()");
            }
        }
        assertTrue(violations.isEmpty(),
                "刷价算日期必须显式给时区（用 supplierZone()），否则正确性系于部署环境的 TZ 变量：" + violations);
    }

    @Test
    @DisplayName("五家都必须显式声明基准时区，且取值一致——否则同一个「今天」在各家不是同一天")
    void everySupplierDeclaresTheSameZoneExplicitly() {
        List<Path> impls = refreshImpls();
        assertEquals(5, impls.size(), "刷价实现应为五家（新接一家请连同本约束一起看）：" + impls);

        List<String> missing = new ArrayList<>();
        for (Path impl : impls) {
            String source = codeOf(impl);
            boolean declares = source.contains("protected ZoneId supplierZone()")
                    && source.contains("ZoneId.of(\"Asia/Shanghai\")");
            if (!declares) {
                missing.add(impl.getFileName().toString());
            }
        }
        assertTrue(missing.isEmpty(),
                "这些家没有显式声明 Asia/Shanghai 作为刷价基准时区：" + missing);
    }

    /**
     * 骨架的兜底仍是 {@code systemDefault()}，这是有意的：兜底若也写死北京，
     * 新接一家忘了覆写就会「碰巧对」，本约束反而抓不到。兜底不安全 + 架构测试兜住，
     * 比兜底安全而无人检查更可靠。
     */
    @Test
    @DisplayName("骨架兜底保持不安全值，逼各家显式声明")
    void skeletonFallbackStaysAmbientOnPurpose() {
        String skeleton = codeOf(Path.of(
                "src/main/java/com/trip/booking/spa/gateway/application/pricing/AbstractCPSQueryPriceService.java"));
        assertTrue(skeleton.contains("return ZoneId.systemDefault();"),
                "骨架兜底一旦改成写死北京，各家漏覆写就查不出来了");
    }
}
