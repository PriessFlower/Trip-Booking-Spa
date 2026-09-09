package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防复活：spa 的静态目录域已于 2026-09-08 整域撤除，代码不许再碰那些表。
 *
 * <p>撤的是什么：Expedia 静态内容摄取 → 加工进目录（hotel_details / room_base /
 * hotel_picture / hotel_extend）与地理建档（country_info / city_info），以及两张供应商
 * 档案表的统一侧列（hotel_id / room_id / merger）。撤的理由不是"没用上"而是边界：
 * 归一与静态内容的家是 trip-booking-agg（R-2.4「聚合的映射表不建在网关」），
 * 而这六张表撤除前全仓零 SELECT、数据停在 2026-08-14、占库约 3 GB。
 *
 * <p>为什么要有这道测试：生产表已 DROP，代码若悄悄写回来，不会编译失败也不会在
 * 本地报错——只会在生产以 1146 (Table doesn't exist) 的形式炸在某条运维路径上。
 * 同类先例是 {@code global_product_supplier}（撤表后靠 R-6.1 的守护测试防复活）。
 *
 * <p>实现方式与 {@code ProductIdentityArchRulesTest} 一致：源码扫描而非 ArchUnit——
 * 一半约束对象在 mapper XML 里，不在字节码中。
 */
class StaticCatalogRetiredArchRulesTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path MAPPER_DIR = Path.of("src/main/resources/mapper");

    /** 已 DROP 的六张表。留 expedia_property_content——那张还在，bff/b2b 详情页与键派生要读。 */
    private static final List<String> DROPPED_TABLES = List.of(
            "hotel_details", "room_base", "hotel_picture", "hotel_extend", "city_info", "country_info");

    @Test
    void droppedCatalogTablesMustNotComeBack() {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(MAIN_JAVA, MAPPER_DIR)) {
            walk(root, path -> {
                String source = read(path);
                for (String table : DROPPED_TABLES) {
                    if (sqlMentions(source, table)) {
                        violations.add(path + " 又引用了已撤除的静态目录表 " + table);
                    }
                }
            });
        }
        assertTrue(violations.isEmpty(),
                "静态目录域已撤除（config/mysql/spa-catalog-schema.sql 有原委）。"
                        + "要静态内容请走 trip-booking-agg，不要在网关重建目录：\n"
                        + String.join("\n", violations));
    }

    /**
     * 统一侧列不许回到供应商档案表：归一属聚合域（R-2.4）。
     *
     * <p>撤除前生产实测 97,409/97,409 行的 hotel_id 就等于 supplier_hotel_id、
     * 356,571/356,571 行的 room_id 等于 supplier_room_id——与 global_product_supplier
     * 当年被撤的同一个病，统一侧只是供应商侧的一份拷贝。
     */
    @Test
    void unifiedIdColumnsMustNotComeBackToSupplierTables() {
        List<String> violations = new ArrayList<>();
        walk(MAPPER_DIR, path -> {
            String source = read(path);
            if (!source.contains("supplier_hotel_base") && !source.contains("supplier_room_base")) {
                return;
            }
            for (String column : List.of("hotel_id", "room_id", "merger")) {
                if (source.matches("(?s).*\\b" + column + "\\b\\s*=.*")
                        || source.matches("(?s).*[(,]\\s*" + column + "\\s*[,)].*")) {
                    violations.add(path + " 在供应商档案表上又写了统一侧列 " + column);
                }
            }
        });
        assertTrue(violations.isEmpty(),
                "归一的落点是 trip-booking-agg，不是网关的档案表（docs/product-identity.md R-2.4）：\n"
                        + String.join("\n", violations));
    }

    /**
     * 只认 SQL 语境里的表名（FROM/JOIN/INTO/UPDATE/TABLE 之后），躲开注释里的历史叙述；
     * 也因此 {@code supplier_room_base} 不会被 {@code room_base} 误伤——表名要紧接关键字。
     */
    private static boolean sqlMentions(String source, String table) {
        return source.matches("(?is).*\\b(from|join|into|update|table)\\s+`?" + table + "`?\\b.*");
    }

    private static void walk(Path root, java.util.function.Consumer<Path> visitor) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml"))
                    .forEach(visitor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
