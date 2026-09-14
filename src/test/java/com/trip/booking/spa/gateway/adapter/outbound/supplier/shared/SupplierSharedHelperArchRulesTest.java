package com.trip.booking.spa.gateway.adapter.outbound.supplier.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住"换一家供应商不用改的东西，不许各家再抄一份"（PROJECT.md §4.1.3 首要判据）。
 *
 * <p><b>为什么要成文</b>：这件事已经付过一次代价。{@code CancelClassifier} 的类注释记着，
 * 三家各抄一份私有的 {@code classifyCancel}，同一句注释抄了三遍、<b>同一个漏也漏了三遍</b>
 * ——都没判退改段是否已过期，生产上 659 条"可免费取消"里 94 条的免费窗早已关闭。
 *
 * <p>2026-09-14 又长出一簇同样的东西：接完道旅、差旅无忧、美团之后，
 * {@code hoursBeforeCheckInEnd}+{@code MIN_BEFORE} 各 3 份、{@code nightsOf} 3 份、
 * {@code childAgesCsv} 3 份（且已经写出三种写法）、{@code outcome(...)} 4 份、
 * 丢弃计数 5 份<b>还分成两个名字</b>（{@code countDropped} 与 {@code countConvertDropped}）。
 * 都还没长出分歧，趁早收掉——本测试是收掉之后的闸。
 *
 * <p><b>本测试只拦"不该各家一份"的，不拦"本该各家一份"的</b>：比如各家绑定自己的
 * {@code SupplierSourceEnum} 常量，那正是 §4.1.3 判为适配层的东西。
 */
class SupplierSharedHelperArchRulesTest {

    private static final Path SUPPLIER_ROOT =
            Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");

    /** 供应商适配层的全部源文件，但不含 {@code shared}——公共实现本来就该住在那里 */
    private static List<Path> supplierSources() {
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/supplier/shared/"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 各家不得自带这些方法——它们换一家供应商一个字都不用改，已各有公共落点。
     */
    @Test
    @DisplayName("住期长度、儿童年龄串、退改 before、验价终态工厂：都不许各家再写一份")
    void supplierAdaptersDoNotRedeclareSharedHelpers() {
        Map<String, String> banned = Map.of(
                "int nightsOf(", "改用 StayDates.nights(checkIn, checkOut)",
                "String childAgesCsv(", "改用 Occupancy.childAgesCsv(childAges)",
                "Integer hoursBeforeCheckInEnd(", "改用 CancelClassifier.beforeHours(at, checkIn, zone)",
                "MIN_BEFORE = ", "改用 CancelClassifier.MIN_BEFORE",
                "CheckPriceResult outcome(", "改用 CheckPriceResult.of(outcome, message)");

        List<String> violations = new ArrayList<>();
        for (Path source : supplierSources()) {
            String code = codeOf(source);
            banned.forEach((snippet, fix) -> {
                if (code.contains(snippet)) {
                    violations.add(source.getFileName() + " 自带了 " + snippet.trim() + " → " + fix);
                }
            });
        }
        assertTrue(violations.isEmpty(),
                "这些方法换一家供应商不用改，按 §4.1.3 归公共层，不许各家再抄一份：" + violations);
    }

    /**
     * 丢弃指标的<b>构造</b>只许有一处。各家可以有自己的一行绑定（供应商常量是适配层的事），
     * 但不许再自己拼指标名与 tag——那正是五份拷贝当初分成两个名字的起点。
     */
    @Test
    @DisplayName("quote_dropped 的指标名与 tag 只在 Monitor 里拼一次")
    void dropMetricIsConstructedInExactlyOnePlace() {
        List<String> offenders = new ArrayList<>();
        for (Path source : supplierSources()) {
            if (codeOf(source).contains("MetricNames.QUOTE_DROPPED")) {
                offenders.add(source.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "丢弃计数请走 Monitor.recordDropped(supplier, stage, reason, count)，别自己拼：" + offenders);
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
     * 本测试的类注释自己就写满了这些方法名，不剥的话它会把自己打红。
     */
    private static String codeOf(Path p) {
        return read(p)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
