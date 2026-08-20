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
     * R-6.1：聚合域的桥（{@code global_product_supplier}）<b>不得存在于本仓</b>。
     *
     * <p>它的用途只有比价检索——把聚合后的统一产品与各家供应商的卖法连起来。而 R-6.1 定案
     * 「聚合不放在供应商网关」、R-6.3「聚合域引用 productKey；网关执行路径不引用聚合产物」。
     * 谁做聚合谁自建这张表，SPA 只负责产出 productKey。
     *
     * <p>它当初在这里，只因 2026-08-07 还原旧中台时一并建了（旧中台 hotel-base 带聚合层）。
     * 撤除前的实况：全仓零 SELECT，统一侧三列是供应商侧的 1:1 拷贝（生产抽样 1000/1000 相同）。
     * 2026-08-20 停写并撤表——本条防复活，重新写它等于把聚合域拖回网关。
     *
     * <p>只认<b>真正的用法</b>，不认提及：方法调用/声明 {@code upsertGlobalProductSupplier(}，
     * 以及 SQL 里 {@code INTO/FROM/UPDATE/JOIN global_product_supplier}。
     * 否则本条会把解释"为什么撤除"的注释自己判成违规——而那些注释正是要留下的。
     */
    @Test
    void R61_aggregationBridgeMustNotComeBack() throws IOException {
        java.util.regex.Pattern usage = java.util.regex.Pattern.compile(
                "upsertGlobalProductSupplier\\s*\\(|(?i:into|from|update|join)\\s+global_product_supplier");
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(MAIN_JAVA, MAPPER_DIR)) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    if (usage.matcher(read(p)).find()) {
                        violations.add(p + " 引用了聚合域的桥 global_product_supplier");
                    }
                });
            }
        }
        assertTrue(violations.isEmpty(),
                "违反 R-6.1（聚合不放在供应商网关，docs/product-identity.md §6）：\n"
                        + String.join("\n", violations));
    }

    /**
     * 四链路类的判定：端点入口 SpaController，以及 gateway 里的
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
        return normalized.contains("/gateway/")
                && (file.contains("Price") || file.contains("CheckPrice") || file.contains("Booking")
                || file.contains("Cancel") || file.contains("OrderQuery"));
    }

    /**
     * legacy 隔离（历史使命已完成）：legacy/ 曾是旧供应商代码的临终关怀区（迁一家删一家），
     * 2026-08-18 整包删除（10 模块 213 文件，生产 24h 零流量实证）。本规则改为守住
     * "不复活"：任何路径下不得再出现 legacy 包目录或对它的 import。
     */
    @Test
    void LEGACY_mustStayDeleted() throws IOException {
        assertTrue(!Files.exists(MAIN_JAVA.resolve("com/trip/booking/spa/legacy")),
                "legacy 包已于 2026-08-18 删除，不得复活——新供应商一律走 gateway 六边形结构");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        if (read(p).contains("com.trip.booking.spa.legacy.")) {
                            violations.add(p + " 引用了已删除的 legacy 包");
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                "legacy 包已删除，不得再被引用：\n" + String.join("\n", violations));
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
