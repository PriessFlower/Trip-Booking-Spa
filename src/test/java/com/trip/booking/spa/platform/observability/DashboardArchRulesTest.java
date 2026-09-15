package com.trip.booking.spa.platform.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 看板文件的两条硬约束（docs/observability.md）。看板是 JSON，编译与单测都碰不到它，
 * 而它恰恰是「这个指标有没有人看」的唯一答案（O-5.1）。
 *
 * <p>本类按 JSON 结构判，不用正则切面板——{@code "type"} 在 datasource、fieldConfig 里
 * 也有，按文本切出来的「面板」全是碎片，断言会对着假数据红（本类第一版即如此）。
 */
class DashboardArchRulesTest {

    private static final Path DASHBOARDS = Path.of("deploy/monitoring/grafana/dashboards");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * O-3.2：每个面板必须有一句话解释，鼠标悬停即可见。没有解释的面板不许合入——
     * 看图的人不该为了读懂一条曲线去翻代码。
     */
    @Test
    @DisplayName("O-3.2：每块面板都得有 description")
    void O32_everyPanelExplainsItself() {
        List<String> offenders = new ArrayList<>();
        for (Path file : dashboards()) {
            for (JsonNode panel : read(file).path("panels")) {
                if ("row".equals(panel.path("type").asText())) {
                    continue;
                }
                if (panel.path("description").asText("").isBlank()) {
                    offenders.add(file.getFileName() + " → " + panel.path("title").asText("(无标题)"));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "这些面板没有一句话解释（O-3.2）：" + offenders);
    }

    /**
     * 看板变量（{@code $supplier} 这类）只有本板声明了 templating 才有值；声明删了而
     * 表达式还留着引用，Grafana <b>不报错</b>，只把它当空串代入——画出来是一条永远查不到
     * 数据的空线，比报错难发现得多。
     *
     * <p>2026-09-15 撤「供应商」板的两个筛选器时踩线：那块板每条表达式都带
     * {@code supplier=~"$supplier",interface=~"$interface"}，漏掉一条就是一块空面板。
     */
    @Test
    @DisplayName("表达式引用的看板变量，本板必须声明")
    void dashboardVariablesMustBeDeclared() {
        // $labels/$value 是 Prometheus 告警模板的，$__ 开头是 Grafana 内置的，都不需要声明
        Pattern variable = Pattern.compile("\\$(?!labels|value|__)([a-zA-Z][a-zA-Z0-9_]*)");
        List<String> offenders = new ArrayList<>();
        for (Path file : dashboards()) {
            JsonNode board = read(file);
            if (board.has("templating")) {
                continue;
            }
            for (JsonNode panel : board.path("panels")) {
                for (JsonNode target : panel.path("targets")) {
                    Matcher m = variable.matcher(target.path("expr").asText(""));
                    while (m.find()) {
                        offenders.add(file.getFileName() + " → " + panel.path("title").asText() + " 用了 $" + m.group(1));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "看板引用了未声明的变量，Grafana 会当空串代入、画出空面板：" + offenders);
    }

    private static List<Path> dashboards() {
        try (Stream<Path> files = Files.walk(DASHBOARDS)) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
