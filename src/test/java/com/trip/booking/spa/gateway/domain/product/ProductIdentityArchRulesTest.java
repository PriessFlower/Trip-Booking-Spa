package com.trip.booking.spa.gateway.domain.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产品身份的架构约束测试（docs/product-identity.md 阶段5）：把最容易被无意破坏的
 * 两条规则钉进 CI。测试名带规则号，报错即指向规则原文。
 *
 * <p>实现方式是源码扫描而非 ArchUnit：约束对象一半在 mapper XML（不在字节码里），
 * 且仓库无 ArchUnit 依赖，为两条断言引入一个框架不划算。
 */
class ProductIdentityArchRulesTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path MAPPER_DIR = Path.of("src/main/resources/mapper");

    /**
     * R-6.2：网关四链路（查价/验价/下单/取消，含订单反查）禁止读取任何对照表。
     *
     * <p>对照表是聚合域资产：概率产生、持续纠错、会被重建。cursor 把订单链路压在
     * 对照表上，产出了卖A订B、供给静默蒸发（订单 2606261523）、嵌合体身份翻转
     * （13,090 家酒店、错身份成交 11 单）。本仓的免疫力靠的就是这条边界。
     */
    @Test
    void R62_gatewayChainsMustNotReadMappingTables() throws IOException {
        List<String> forbidden = List.of(
                "global_product_supplier", "GlobalProductSupplier",
                "amap_expedia_match", "room_physical_mapping", "hotel_base_mapping");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(ProductIdentityArchRulesTest::isGatewayChainClass)
                    .forEach(p -> {
                        String source = read(p);
                        for (String token : forbidden) {
                            if (source.contains(token)) {
                                violations.add(p + " 引用了对照表标识 \"" + token + "\"");
                            }
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                "违反 R-6.2（网关四链路禁止读对照表，docs/product-identity.md §6）：\n" + String.join("\n", violations));
    }

    /**
     * 四链路类的判定：端点入口 SpaController，以及 gateway（含 legacy 存量）里的
     * 查价/验价/下单/取消/订单反查类。目录建档（content 包）是供货侧，不在四链路内。
     */
    private static boolean isGatewayChainClass(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/content/")) {
            return false;
        }
        String file = path.getFileName().toString();
        if (file.equals("SpaController.java")) {
            return true;
        }
        return (normalized.contains("/gateway/") || normalized.contains("/legacy/"))
                && (file.contains("Price") || file.contains("CheckPrice") || file.contains("Booking")
                || file.contains("Cancel") || file.contains("OrderQuery"));
    }

    /**
     * legacy 隔离：legacy/ 是旧供应商代码的临终关怀区（迁一家删一家），新结构
     * （gateway/platform/bootstrap）不得反向依赖它——否则死代码永远拔不掉。
     */
    @Test
    void LEGACY_isolationNewCodeMustNotImportLegacy() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String n = p.toString().replace('\\', '/');
                        return n.contains("/gateway/") || n.contains("/platform/") || n.contains("/bootstrap/");
                    })
                    .forEach(p -> {
                        if (read(p).contains("com.trip.booking.spa.legacy.")) {
                            violations.add(p + " import 了 legacy 包");
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                "新结构不得依赖 legacy（迁移隔离区，只出不进）：\n" + String.join("\n", violations));
    }

    /**
     * R-2.1：易腐令牌不得写入 MySQL。
     *
     * <p>检查所有 mapper XML 的写语句里没有出现已知的令牌字段名。反面教材：cursor 把
     * 4 小时轮换的报价码当主数据落库，KR 静态库 25.6% 映射是死 id、339 个死 id 残留
     * 66,469 行在售僵尸价。哪家供应商的码算令牌，以 SupplierIdentityProfile 申报为准——
     * 这里列的是各家已知令牌字段的持久化拼写。
     */
    @Test
    void R21_perishableTokensMustNotBePersisted() throws IOException {
        List<String> forbidden = List.of(
                "plan_session", "planSession",
                "book_hash", "match_hash",
                "rate_key", "rateKey",
                "plansid", "rpid",
                "prebook_token", "prebookToken",
                "goods_uniq_id", "goodsUniqId",
                "little_majia_id", "littleMajiaId", "majia_id");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAPPER_DIR)) {
            files.filter(p -> p.toString().endsWith(".xml")).forEach(p -> {
                String xml = read(p);
                for (String token : forbidden) {
                    if (xml.contains(token)) {
                        violations.add(p + " 出现令牌字段 \"" + token + "\"");
                    }
                }
            });
        }
        assertTrue(violations.isEmpty(),
                "违反 R-2.1（易腐令牌禁止落库，docs/product-identity.md §2）：\n" + String.join("\n", violations));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("读不到 " + path, e);
        }
    }
}
