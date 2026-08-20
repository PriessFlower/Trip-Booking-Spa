package com.trip.booking.spa.gateway.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 钉住 R-2.7 / R-2.8：<b>身份与成分必须同源、且成分不得降维</b>。
 *
 * <p>productKey 是 sha256，单向不可逆。成分若只在派生时存在、返回时丢弃，下游想入库
 * 就只能拿原始响应重判一遍——而重判必然降维：2026-08-20 复盘发现建档把
 * {@code B1L1D1}（含三餐）与 {@code B1L0D0}（只含早）一起压成 {@code breakfast=1}，
 * 占用干脆没有列。表因此既没有原信息、也无法从身份列反推。
 */
class ProductIdentityTest {

    private static final int ELONG = 10010;

    @Test
    @DisplayName("身份与成分同源：productKey 必须等于用同一批成分直接派生的结果")
    void keyMatchesItsOwnComponents() {
        MealSignature meal = MealSignature.known(true, true, true);
        CancelClass cancel = CancelClass.FREE_CANCELLABLE;

        ProductIdentity id = ProductIdentity.of(ELONG, "acct", "H1", "R1", meal, cancel, "2");

        assertEquals(ProductKeyFactory.derive(ELONG, "acct", "H1", "R1", meal, cancel, "2"),
                id.productKey(), "identity 的 key 与直接派生必须一致，否则成分与身份对不上");
    }

    @Test
    @DisplayName("成分原样带出，不降维")
    void componentsAreCarriedVerbatim() {
        ProductIdentity id = ProductIdentity.of(ELONG, "acct", "H1", "R1",
                MealSignature.known(true, false, false), CancelClass.NON_REFUNDABLE, "2-9,4");

        assertEquals(ELONG, id.supplierCode());
        assertEquals("acct", id.account());
        assertEquals("H1", id.supplierHotelId());
        assertEquals("R1", id.supplierRoomId());
        assertEquals("B1L0D0", id.mealSignature());
        assertEquals("NON_REFUNDABLE", id.cancelClass());
        assertEquals("2-9,4", id.occupancy());
    }

    /**
     * 这条是整件事的由来：旧口径 {@code breakfast INT} 把下面两者都写成 1。
     */
    @Test
    @DisplayName("全餐与只含早的餐食成分必须可辨——旧的 breakfast 0/1 分不出来")
    void fullBoardIsDistinguishableFromBreakfastOnly() {
        ProductIdentity fullBoard = ProductIdentity.of(ELONG, "acct", "H1", "R1",
                MealSignature.known(true, true, true), CancelClass.FREE_CANCELLABLE, "2");
        ProductIdentity breakfastOnly = ProductIdentity.of(ELONG, "acct", "H1", "R1",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2");

        assertEquals("B1L1D1", fullBoard.mealSignature());
        assertEquals("B1L0D0", breakfastOnly.mealSignature());
        assertNotEquals(fullBoard.mealSignature(), breakfastOnly.mealSignature(),
                "压成 breakfast=1 就是在这里丢的信息");
        assertNotEquals(fullBoard.productKey(), breakfastOnly.productKey(),
                "前提：它们本就是两个不同的卖法");
    }

    /**
     * 占用是唯一一个连列都没有的成分。同一 (酒店,房型,餐食,退改) 下，占用不同即不同卖法——
     * 生产实测有 1,359 组从表上看完全一样、productKey 却不同的档案。
     */
    @Test
    @DisplayName("占用不同即不同身份，且占用串必须带出来")
    void occupancyIsPartOfIdentityAndIsCarried() {
        MealSignature meal = MealSignature.known(true, false, false);
        ProductIdentity two = ProductIdentity.of(ELONG, "acct", "H1", "R1", meal, CancelClass.FREE_CANCELLABLE, "2");
        ProductIdentity three = ProductIdentity.of(ELONG, "acct", "H1", "R1", meal, CancelClass.FREE_CANCELLABLE, "3");

        assertNotEquals(two.productKey(), three.productKey());
        assertEquals("2", two.occupancy());
        assertEquals("3", three.occupancy());
    }

    /** UNKNOWN 是合法成分（可进 key、可实时售卖），只是不进目录（R-5.4） */
    @Test
    void unknownComponentsAreCarriedAsUnknown() {
        ProductIdentity id = ProductIdentity.of(ELONG, "acct", "H1", "R1",
                MealSignature.unknown(), CancelClass.UNKNOWN, "2");

        assertEquals("UNKNOWN", id.mealSignature());
        assertEquals("UNKNOWN", id.cancelClass());
    }
}
