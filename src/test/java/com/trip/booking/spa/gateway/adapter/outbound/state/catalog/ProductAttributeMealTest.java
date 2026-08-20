package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 由档案表的餐食规范串还原 {@link Meal}。
 *
 * <p><b>为什么单独写这组</b>：改造前 {@code toMeal()} 只能
 * {@code setLunchCount(0)}/{@code setDinnerCount(0)} 硬填 0——旧列 {@code breakfast INT}
 * 只有一位，午晚餐在落库那一刻就丢了。档案表按 R-2.7 改存规范串后三顿都能还原，
 * 而这件事此前<b>没有任何测试守着</b>（2026-08-20 靠反证发现：把午餐改回硬填 0，全绿）。
 */
class ProductAttributeMealTest {

    private static Meal mealOf(String signature) {
        return ProductAttributeReader.ProductAttribute.builder()
                .mealSignature(signature).build().toMeal();
    }

    @Test
    @DisplayName("含三餐：早/午/晚都要还原出来")
    void fullBoard() {
        Meal m = mealOf("B1L1D1");
        assertEquals(1, m.getCount());
        assertEquals(1, m.getLunchCount());
        assertEquals(1, m.getDinnerCount());
    }

    @Test
    @DisplayName("只含早：午晚必须是 0，且不能把早餐也丢了")
    void breakfastOnly() {
        Meal m = mealOf("B1L0D0");
        assertEquals(1, m.getCount());
        assertEquals(0, m.getLunchCount());
        assertEquals(0, m.getDinnerCount());
    }

    @Test
    @DisplayName("含早+晚餐（半包）——旧的 breakfast 0/1 与只含早分不开的那一类")
    void halfBoard() {
        Meal m = mealOf("B1L0D1");
        assertEquals(1, m.getCount());
        assertEquals(0, m.getLunchCount());
        assertEquals(1, m.getDinnerCount());
    }

    @Test
    @DisplayName("无餐食")
    void noMeal() {
        Meal m = mealOf("B0L0D0");
        assertEquals(0, m.getCount());
        assertEquals(0, m.getLunchCount());
        assertEquals(0, m.getDinnerCount());
    }

    /**
     * UNKNOWN 本不该进目录（R-5.4），但库是长期资产、历史数据与人工订正都可能留下它。
     * 读到了一律按"不含"——猜"含"是卖错，猜"不含"最多少卖（R-1.6）。
     */
    @Test
    @DisplayName("串不合法一律按不含，绝不猜")
    void unparseableMeansNoMeal() {
        for (String s : new String[]{"UNKNOWN", null, "", "B1", "XXXXXX"}) {
            Meal m = mealOf(s);
            assertEquals(0, m.getCount(), "signature=" + s);
            assertEquals(0, m.getLunchCount(), "signature=" + s);
            assertEquals(0, m.getDinnerCount(), "signature=" + s);
        }
    }
}
