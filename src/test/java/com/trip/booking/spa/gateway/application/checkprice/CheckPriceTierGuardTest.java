package com.trip.booking.spa.gateway.application.checkprice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验价分档必须被实现方兑现（O-2 契约字段不许静默无效）。
 *
 * <p>{@code verifyLevel} 是契约里的一档：{@code AVAILABILITY}=渠道曝光核价，只问"还在售"；
 * {@code BOOKABLE}=下单前核价，要句柄。上游按它给<b>不同的预算</b>——曝光档 1.5s。
 *
 * <p>2026-09-02 事故：飞猪的实现<b>压根没读这个字段</b>，曝光核价照打完整 validate
 * （实测 1,833ms + 现取一趟），超时被兜底成「不可预订」，报价在高德列表页整条消失。
 * 三家里只有艺龙兑现了这档——同一个坑艺龙 2026-08 踩过、修了、没人知道。
 *
 * <p>本测试盯的是"下一家"：验价走两趟供应商调用的实现，必须显式处理 AVAILABILITY，
 * 否则它一定会在曝光层超时，而且症状（列表页没有这家的价）离病因很远。
 */
class CheckPriceTierGuardTest {

    private static final Path SUPPLIER_ROOT =
            Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");

    /**
     * 已知欠账：Expedia 的验价同样是两趟（现取 + price_check），同样能在第一趟后截断，
     * 但它<b>尚未接入渠道曝光核价</b>（cursor 的 {@code spaGateway.checkprice} 只有
     * elong,fliggy），没有预算压力，故暂不实现。
     *
     * <p>接入曝光层前必须补上并从本清单删除——否则会与飞猪 2026-09-02 同款超时。
     * 清单刻意写在测试里而不是注释里：它会随代码一起被 review，也不会腐烂成沉默的例外。
     */
    private static final Set<String> KNOWN_GAP = Set.of("ExpediaPriceServiceImpl.java");

    @Test
    @DisplayName("两趟验价的实现必须兑现 AVAILABILITY 档，不许打完整验价")
    void twoStageCheckMustHonourAvailabilityTier() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                // 一个文件里出现两次以上 CHECK_PRICE 用途的出网调用＝两趟验价
                int stages = src.split("CallPurpose\\.CHECK_PRICE", -1).length - 1;
                if (stages < 2) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (KNOWN_GAP.contains(name)) {
                    continue;
                }
                if (!src.contains("VerifyLevel.AVAILABILITY")) {
                    violations.add(name + "（" + stages + " 趟供应商调用，却未读 verifyLevel）");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "验价走两趟的实现必须在第一趟后按 AVAILABILITY 截断（照 ElongPriceServiceImpl"
                        + "#availabilityOnlyResp / FliggyPriceServiceImpl#availabilityOnlyResp）："
                        + violations);
    }

    @Test
    @DisplayName("欠账清单里的文件必须真的存在——清单不许腐烂")
    void knownGapEntriesMustStillExist() throws IOException {
        List<String> stale = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SUPPLIER_ROOT)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            for (String gap : KNOWN_GAP) {
                if (!names.contains(gap)) {
                    stale.add(gap);
                }
            }
        }
        assertTrue(stale.isEmpty(), "KNOWN_GAP 里的文件已不存在，请一并删掉该条目：" + stale);
    }
}
