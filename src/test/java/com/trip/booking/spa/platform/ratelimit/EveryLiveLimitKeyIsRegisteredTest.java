package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码真会打出去的每一个限流键，都必须在配置示例里登记。
 *
 * <p><b>补的是哪个洞</b>：check-nacos-key-drift.py 比的是「example ↔ 代码读的键」与
 * 「example ↔ Nacos」，两头都<b>不知道限流器实际会用哪些桶</b>——桶名是 map 里的值，
 * 不是配置键。于是漏登记一个桶，两道检查都是绿的，那个接口就静静地吃 default-qps。
 *
 * <p>2026-08-25 实测漏了两个：{@code EXPEDIA:SPA_SUPPLIER_API_CANCEL_ORDER}（客人取消房间）
 * 与 {@code EXPEDIA:SPA_SUPPLIER_FILE_DOWNLOAD}（静态数据分块下载），都按 default-qps=20
 * 在打 Expedia，而没有任何人知道。漏登记本身不报错——这正是要一条测试盯着的理由。
 *
 * <p>键的两种来源都查：代码里写死的 {@code "GLOBAL_LIMIT:..."} 字面量，以及
 * {@code BaseHttpAccess} 由「供应商 + MonitorNameEnum」拼出来的那些。供应商从包路径推断
 * （architecture.md §2 规定供应商语义只能出现在各自适配层，故路径可信）。
 */
class EveryLiveLimitKeyIsRegisteredTest {

    private static final Path EXAMPLE = Path.of("config/nacos/trip-booking-spa.yaml.example");
    private static final Path SUPPLIER_ROOT =
            Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    @DisplayName("写死在代码里的 GLOBAL_LIMIT 字面量必须已登记")
    void hardcodedKeysAreRegistered() throws IOException {
        String example = Files.readString(EXAMPLE);
        Pattern literal = Pattern.compile("\"(GLOBAL_LIMIT:[A-Z0-9_:]+)\"");
        Set<String> missing = new TreeSet<>();

        for (Path file : javaFiles(MAIN)) {
            Matcher m = literal.matcher(Files.readString(file));
            while (m.find()) {
                String key = m.group(1);
                // 用途桶（三段以上）由 CallPurpose 拼出，接口桶登记即可覆盖其存在性
                if (!example.contains("[" + key + "]")) {
                    missing.add(key + "  ←  " + file.getFileName());
                }
            }
        }
        assertTrue(missing.isEmpty(), "这些限流键代码在用、example 未登记，"
                + "于是它们在跑 default-qps 而没人知道：\n  " + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("供应商通道按「供应商 + MonitorNameEnum」拼出的键必须已登记")
    void composedKeysAreRegistered() throws IOException {
        String example = Files.readString(EXAMPLE);
        Pattern used = Pattern.compile("MonitorNameEnum\\.([A-Z0-9_]+)");
        Set<String> missing = new TreeSet<>();
        int checked = 0;

        for (Path file : javaFiles(SUPPLIER_ROOT)) {
            String supplier = supplierFromPath(file);
            if (supplier == null) {
                continue;
            }
            String src = Files.readString(file);
            Matcher m = used.matcher(src);
            while (m.find()) {
                String key = "GLOBAL_LIMIT:" + supplier + ":" + m.group(1);
                checked++;
                if (!example.contains("[" + key + "]")) {
                    missing.add(key + "  ←  " + file.getFileName());
                }
            }
        }

        assertTrue(checked >= 6, "只扫到 " + checked + " 处 MonitorNameEnum 用法，"
                + "适配层结构可能已变，本测试需同步——否则它会在什么都没查的情况下报绿");
        assertTrue(missing.isEmpty(), "这些限流键代码在用、example 未登记，"
                + "于是它们在跑 default-qps 而没人知道：\n  " + String.join("\n  ", missing));
    }

    /** 供应商取自包路径：.../supplier/<名字>/... */
    private static String supplierFromPath(Path file) {
        String path = file.toString().replace('\\', '/');
        Matcher m = Pattern.compile("/supplier/([a-z0-9]+)/").matcher(path);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        return files;
    }
}
