package com.trip.booking.spa.core.api.common.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 productKey 的派生纪律（docs/product-identity.md §1）。
 *
 * <p>最要紧的两条：<b>确定性</b>（同事实永远同键，R-1.5 派生不发号的根基）与
 * <b>成分敏感</b>（任一成分变则键必变——成分失去区分度，resolve 就可能拿别的
 * 卖法顶替，那是"卖错"，违反 R-1.6）。
 */
class ProductKeyFactoryTest {

    private static String derive() {
        return ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2");
    }

    /** 同事实永远同键：目录幂等、跨查价可对齐，全靠这条 */
    @Test
    void sameFactsAlwaysSameKey() {
        assertEquals(derive(), derive());
    }

    /** sha256 hex，恰好适配目录表现有 product_id VARCHAR(64) */
    @Test
    void keyIs64CharLowerHex() {
        assertTrue(derive().matches("[0-9a-f]{64}"));
    }

    /** 任一成分变，键必变——七个成分逐一验证区分度 */
    @Test
    void everyComponentChangesTheKey() {
        String base = derive();
        assertNotEquals(base, ProductKeyFactory.derive(10003, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2"), "供应商");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2C_SA_MOD_XSELL_APP", "11775754", "230410389",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2"), "账号（R-1.3 汇智双账号教训）");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "19194", "230410389",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2"), "酒店");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "314159",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2"), "房型");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(false, false, false), CancelClass.FREE_CANCELLABLE, "2"), "餐食");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(true, false, false), CancelClass.NON_REFUNDABLE, "2"), "退改类");
        assertNotEquals(base, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2-9,4"), "占用");
    }

    /** 只含午餐 ≠ 只含晚餐：命名分类的 OTHER 兜底会把两者折成一类，位级签名不会 */
    @Test
    void lunchOnlyAndDinnerOnlyAreDistinct() {
        String lunchOnly = ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(false, true, false), CancelClass.FREE_CANCELLABLE, "2");
        String dinnerOnly = ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(false, false, true), CancelClass.FREE_CANCELLABLE, "2");
        assertNotEquals(lunchOnly, dinnerOnly);
    }

    /** UNKNOWN 是合法输入（实时链路可流转，目录另有闸），且与任何已知值不同键 */
    @Test
    void unknownIsLegalAndDistinct() {
        String unknown = ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.unknown(), CancelClass.UNKNOWN, "2");
        assertTrue(unknown.matches("[0-9a-f]{64}"));
        assertNotEquals(derive(), unknown);
        assertNotEquals(unknown, ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT", "11775754", "230410389",
                MealSignature.known(false, false, false), CancelClass.UNKNOWN, "2"),
                "餐食 UNKNOWN ≠ 明确无餐——不确定不许说成确定");
    }

    /** 身份成分缺席或含分隔符，宁可炸在派生处，不许生成歧义键 */
    @Test
    void missingOrMalformedComponentThrows() {
        assertThrows(IllegalArgumentException.class, () -> ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT",
                " ", "230410389", MealSignature.unknown(), CancelClass.UNKNOWN, "2"));
        assertThrows(IllegalArgumentException.class, () -> ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT",
                "11775754", null, MealSignature.unknown(), CancelClass.UNKNOWN, "2"));
        assertThrows(IllegalArgumentException.class, () -> ProductKeyFactory.derive(10005, "B2B_SA_PKG_MOD_AGENT",
                "117|75754", "230410389", MealSignature.unknown(), CancelClass.UNKNOWN, "2"));
    }
}
