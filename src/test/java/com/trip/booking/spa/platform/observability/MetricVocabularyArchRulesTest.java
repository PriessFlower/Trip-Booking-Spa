package com.trip.booking.spa.platform.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指标口径的架构约束（docs/observability.md §2、§3）。测试名带规则号，报错即指向规则原文。
 *
 * <p>实现方式是源码扫描：约束的是「写埋点时怎么写」，字节码里看不出字面量是从常量来的
 * 还是手打的。沿用 {@code ProductIdentityArchRulesTest} 的做法，不为几条断言引框架。
 *
 * <p>这些规则不成文就一定会被违反——它们每一条都对应着已经发生过的事：三种 supplier
 * 方言并存导致指标拼不起来、维度拼进名字最多产生上千个名字、{@code ok} 记两遍导致空结果
 * 占比系统性偏低。
 */
class MetricVocabularyArchRulesTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    /** 埋点所在文件（含 {@code Monitor.record}）才受标签字面量约束；业务代码里的 "status" 是 JSON 字段名 */
    private static boolean hasInstrumentation(String source) {
        return source.contains("Monitor.record");
    }

    /**
     * O-2.3：{@code supplier} 标签值一律取 {@link com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum#name()}。
     *
     * <p>禁止小写字面量与数字编码。此前 {@code catalog_attribute_hit{supplier="10010"}} 与
     * {@code supplier_io_access{supplier="ELONG"}} 无法在 PromQL 里 join，
     * 「无房型映射丢多少」因此在指标通道上无解。
     */
    @Test
    void O23_supplierTagValueMustComeFromEnum() {
        Pattern literal = Pattern.compile("SUPPLIER,\\s*\"|\"supplier\",\\s*(\"|String\\.valueOf)");
        List<String> violations = scan(source -> {
            List<String> hits = new ArrayList<>();
            Matcher m = literal.matcher(source);
            while (m.find()) {
                hits.add(m.group());
            }
            return hits;
        });
        assertTrue(violations.isEmpty(),
                "supplier 标签值必须取枚举名，不得用字面量或数字编码（O-2.3）：" + violations);
    }

    /**
     * O-2.1：维度进 tag，不进名字。
     *
     * <p>判据：埋点名里不得出现供应商名。反面是撤掉的两处——{@code ChunkedFileAccess} 曾把
     * 供应商与接口拼进名字，艺龙验价曾按结果拆成三个名字。
     */
    @Test
    void O21_dimensionsMustNotBeInMetricNames() {
        Pattern namedBySupplier = Pattern.compile("\"(elong|expedia)_[a-z_]+\"", Pattern.CASE_INSENSITIVE);
        Pattern joinedName = Pattern.compile("Monitor\\.record\\w+\\(\\s*JOINER\\.join");
        List<String> violations = scan(source -> {
            if (!hasInstrumentation(source)) {
                return List.of();
            }
            List<String> hits = new ArrayList<>();
            Matcher m = namedBySupplier.matcher(source);
            while (m.find()) {
                hits.add(m.group());
            }
            if (joinedName.matcher(source).find()) {
                hits.add("JOINER.join 拼指标名");
            }
            return hits;
        });
        assertTrue(violations.isEmpty(), "指标名里不得含供应商等维度（O-2.1）：" + violations);
    }

    /**
     * O-2.2：埋点名不得自带后缀——{@link Monitor} 追加 {@code _count}/{@code _time}/{@code _value}，
     * Prometheus 再给 counter 追加 {@code _total}。自带会得到 {@code xxx_count_count}。
     */
    @Test
    void O22_metricNamesMustNotCarrySuffixes() {
        List<String> offenders = new ArrayList<>();
        for (String name : declaredMetricNames()) {
            if (name.endsWith("_count") || name.endsWith("_time") || name.endsWith("_value")
                    || name.endsWith("_total")) {
                offenders.add(name);
            }
        }
        assertTrue(offenders.isEmpty(), "埋点名不得自带后缀（O-2.2）：" + offenders);
    }

    /**
     * O-3.1：{@code status} 标签值只出自 {@link CallStatus}，禁止手打字面量。
     *
     * <p>此前是 {@code ok} / {@code all} / {@code success} 这类没有宾语的词，看图时无法判断
     * 「什么 ok」；而 {@code ok} 的实际含义还是「全部调用」而非「非空调用」。
     */
    @Test
    void O31_statusTagValueMustComeFromCallStatus() {
        Pattern literal = Pattern.compile("(STATUS|\"status\"),\\s*\"");
        List<String> violations = scan(source -> {
            if (!hasInstrumentation(source)) {
                return List.of();
            }
            List<String> hits = new ArrayList<>();
            Matcher m = literal.matcher(source);
            while (m.find()) {
                hits.add(m.group());
            }
            return hits;
        });
        assertTrue(violations.isEmpty(), "status 取值必须来自 CallStatus 枚举（O-3.1）：" + violations);
    }

    /** O-3.1：取值必须自解释。{@code ok} / {@code all} / {@code success} 这类无宾语的词不得复活 */
    @Test
    void O31_bannedVaguePastValuesMustNotComeBack() {
        List<String> banned = List.of("ok", "all", "succ", "success", "fail");
        List<String> offenders = new ArrayList<>();
        for (CallStatus status : CallStatus.values()) {
            if (banned.contains(status.tagValue())) {
                offenders.add(status.tagValue());
            }
        }
        assertTrue(offenders.isEmpty(), "取值必须带宾语，不得是 ok/all/success 这类词（O-3.1）：" + offenders);
    }

    /** 标签值一律小写，与 Prometheus 惯例一致 */
    @Test
    void O31_callStatusValuesAreLowerCase() {
        for (CallStatus status : CallStatus.values()) {
            assertEquals(status.tagValue().toLowerCase(Locale.ROOT), status.tagValue(),
                    status + " 的标签值必须小写");
        }
    }

    /** 六态互斥且穷尽（O-3.3）。改动这个集合必须同步改看板与告警表达式（O-5.3） */
    @Test
    void O33_callStatusSetIsTheAgreedSix() {
        assertEquals(List.of("quoted", "no_inventory", "rejected", "throttled", "timeout", "error"),
                Stream.of(CallStatus.values()).map(CallStatus::tagValue).toList(),
                "调用终态词表变了：看板与告警里的 status 表达式必须同步改（O-5.3）");
    }

    private static List<String> declaredMetricNames() {
        Pattern p = Pattern.compile("public static final String \\w+ = \"([a-z_]+)\";");
        Matcher m = p.matcher(read(MAIN_JAVA.resolve(
                "com/trip/booking/spa/platform/observability/MetricNames.java")));
        List<String> names = new ArrayList<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        assertTrue(names.size() > 10, "MetricNames 里应当有全部埋点名，实测只扫到 " + names.size() + " 个");
        return names;
    }

    private interface SourceCheck {
        List<String> violationsIn(String source);
    }

    private static List<String> scan(SourceCheck check) {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    // 唯一出处自己要写字面量，否则无处可写
                    .filter(p -> !p.getFileName().toString().equals("MetricTags.java"))
                    .filter(p -> !p.getFileName().toString().equals("MetricNames.java"))
                    .forEach(p -> {
                        for (String hit : check.violationsIn(read(p))) {
                            violations.add(p.getFileName() + " -> " + hit);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return violations;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
