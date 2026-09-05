package com.trip.booking.spa.gateway.application.checkprice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验价流程只许长在 {@link AbstractCheckPriceFlow} 上（R-3.6：全部供应商共用同一条管线，
 * 禁止 per-supplier 救回补丁）。
 *
 * <p>为什么要钉：{@code ResolveGate} 一直是共用的，但「令牌死了先换票再判 RATE_DEAD」和
 * 「AVAILABILITY 档不打 validate」此前都是每家实现里靠记性写的几行——艺龙写了、Expedia 抄了、
 * 飞猪没抄，CI 全绿，生产 44% 验价 RATE_DEAD 而酒店明明有货（2026-09-05，8/18）。
 * 分档那条 2026-09-02 也同样漏过一次。本测试让第四家<b>漏不掉</b>：不接模板编不过路由，
 * 自己写 resolve / 读 verifyLevel 在这里红。
 */
class CheckPriceFlowArchRulesTest {

    private static final Path SUPPLIER_ROOT =
            Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");

    /**
     * 登记在册的欠账：Expedia 尚未接入渠道曝光核价（cursor 的 {@code spaGateway.checkprice}
     * 只有 elong,fliggy），其 {@code availabilityOnlyResp} 暂委派给 {@code validate}。
     * 接入曝光层前必须实现真正的仅现货应答并从本清单删除，否则会与飞猪 2026-09-02 同款超时。
     * 清单刻意写在测试里而不是注释里：它会随代码一起被 review，也不会腐烂成沉默的例外。
     */
    private static final Set<String> KNOWN_GAP = Set.of("ExpediaCheckPriceServiceImpl.java");

    @Test
    @DisplayName("每个验价入口必须继承 AbstractCheckPriceFlow，不许直接继承旧模板自己写流程")
    void everyCheckPriceEntryExtendsTheFlow() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : checkPriceEntries()) {
            String src = read(file);
            if (!src.contains("extends AbstractCheckPriceFlow<")) {
                violations.add(file.getFileName().toString());
            }
        }
        assertTrue(violations.isEmpty(),
                "验价入口必须 extends AbstractCheckPriceFlow<现货, 票>（流程归模板，供应商只填钩子）：" + violations);
    }

    @Test
    @DisplayName("供应商包里不得自己换票、自己分档——这两步只在模板里")
    void suppliersMustNotReimplementResolveOrTiering() throws IOException {
        List<String> forbidden = List.of("ResolveGate", "VerifyLevel.", "tryResolveByProductKey",
                "lookupTotalPriceFromCache", "pickCheapestWithinTolerance");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = read(file);
                for (String token : forbidden) {
                    if (src.contains(token)) {
                        violations.add(file.getFileName() + " 引用了 " + token);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "换票（resolve）与分档（AVAILABILITY/BOOKABLE）由 AbstractCheckPriceFlow 统一执行，"
                        + "供应商包只提供候选与两档应答：" + violations);
    }

    /** 曝光档不许偷懒委派给 validate——那等于没有这档（飞猪 2026-09-02 的超时就是这么来的） */
    @Test
    @DisplayName("availabilityOnlyResp 不得委派 validate，除非登记在 KNOWN_GAP")
    void availabilityTierMustNotDelegateToValidate() throws IOException {
        Pattern delegating = Pattern.compile(
                "availabilityOnlyResp\\([^)]*\\)\\s*\\{[^}]*\\bvalidate\\(", Pattern.DOTALL);
        List<String> violations = new ArrayList<>();
        for (Path file : checkPriceEntries()) {
            String name = file.getFileName().toString();
            if (KNOWN_GAP.contains(name)) {
                continue;
            }
            if (delegating.matcher(read(file)).find()) {
                violations.add(name);
            }
        }
        assertTrue(violations.isEmpty(),
                "曝光档必须只答「还在售」、不打 validate（照 ElongPriceServiceImpl / FliggyPriceServiceImpl"
                        + " 的 availabilityOnlyResp）：" + violations);
    }

    @Test
    @DisplayName("欠账清单里的文件必须真的存在——清单不许腐烂")
    void knownGapEntriesMustStillExist() throws IOException {
        List<String> names = checkPriceEntries().stream().map(p -> p.getFileName().toString()).toList();
        List<String> stale = KNOWN_GAP.stream().filter(gap -> !names.contains(gap)).toList();
        assertTrue(stale.isEmpty(), "KNOWN_GAP 里的文件已不存在，请从清单删除：" + stale);
    }

    private static List<Path> checkPriceEntries() throws IOException {
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            return files.filter(p -> p.toString().replace('\\', '/').contains("/checkprice/"))
                    .filter(p -> p.getFileName().toString().endsWith("CheckPriceServiceImpl.java"))
                    .toList();
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
