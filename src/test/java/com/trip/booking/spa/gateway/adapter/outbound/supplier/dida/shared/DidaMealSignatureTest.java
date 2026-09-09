package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住餐食判据：按官方餐型表判（分销码 = 渠道码 + 1），<b>表外与自相矛盾的一律 UNKNOWN</b>。
 *
 * <p>偏移量由数据定案：分销 {@code MealType=1} 的 2,063 条实测 {@code MealAmount} 无一例外为 0，
 * 若两侧同码则 1=Breakfast Included，含早却零份餐不可能；且 4,279 条报价里从未出现 0
 * （2026-09-09 生产实测）。
 *
 * <p>本测试守的是两头：<b>该判出来的要判出来</b>（3/7 是早+晚，不许再兜成 UNKNOWN 白丢），
 * <b>不该判的绝不硬判</b>（PKG、斋月餐系、表外新值、逐晚不一致、份数与餐食矛盾）——
 * 尤其不许把早+晚说成仅含早，那是卖错（R-1.6：赌错只许少卖，不许卖错）。
 */
class DidaMealSignatureTest {

    private final DidaProductKeyDeriver deriver = new DidaProductKeyDeriver();

    @Test
    @DisplayName("MealType=1 且份数为 0：确定无餐")
    void noMeal() {
        Meal meal = deriver.convertMeal(plan(new int[][]{{1, 0}}));
        assertNotNull(meal);
        assertEquals(0, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(0, meal.getDinnerCount());
    }

    @Test
    @DisplayName("MealType=2 且份数>0：确定含早，份数取逐晚最大")
    void breakfastOnly() {
        Meal meal = deriver.convertMeal(plan(new int[][]{{2, 2}, {2, 1}}));
        assertNotNull(meal);
        assertEquals(2, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(0, meal.getDinnerCount());
    }

    @Test
    @DisplayName("MealType=7（渠道 6 BreakfastAndDinner）：早+晚，绝不能落成仅含早")
    void breakfastAndDinner() {
        Meal meal = deriver.convertMeal(plan(new int[][]{{7, 2}}));
        assertNotNull(meal);
        assertEquals(2, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(2, meal.getDinnerCount());
    }

    @Test
    @DisplayName("MealType=3（渠道 2 Half-Board）：早+晚")
    void halfBoard() {
        Meal meal = deriver.convertMeal(plan(new int[][]{{3, 1}}));
        assertNotNull(meal);
        assertEquals(1, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(1, meal.getDinnerCount());
    }

    @Test
    @DisplayName("MealType=4/5（全食宿、全包）：三餐都算")
    void fullBoardAndAllInclusive() {
        for (int type : new int[]{4, 5}) {
            Meal meal = deriver.convertMeal(plan(new int[][]{{type, 2}}));
            assertNotNull(meal, "MealType=" + type);
            assertEquals(2, meal.getCount());
            assertEquals(2, meal.getLunchCount());
            assertEquals(2, meal.getDinnerCount());
        }
    }

    @Test
    @DisplayName("MealType=6（渠道 5 Dinner）：只有晚餐，早餐数必须是 0")
    void dinnerOnly() {
        Meal meal = deriver.convertMeal(plan(new int[][]{{6, 2}}));
        assertNotNull(meal);
        assertEquals(0, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(2, meal.getDinnerCount());
    }

    /**
     * 表里有、但映射不进本仓「早/午/晚」模型的那几类，必须留在 UNKNOWN：
     * 9=PKG(Room&amp;Ticket) 是房+票打包不是餐食；12~15 是斋月的 Suhur/Iftar 系，
     * 与早/午/晚不是同一套划分，且 15 = "Suhur or Iftar" 自带"或"。
     */
    @Test
    @DisplayName("PKG 与斋月餐系不硬塞进早/午/晚，仍为 UNKNOWN")
    void unmappableTypesStayUnknown() {
        for (int type : new int[]{9, 12, 13, 14, 15}) {
            assertNull(deriver.convertMeal(plan(new int[][]{{type, 2}})), "MealType=" + type);
        }
    }

    @Test
    @DisplayName("表外的新值一律 UNKNOWN，不许兜成任何确定餐食")
    void offTableTypeIsUnknown() {
        assertNull(deriver.convertMeal(plan(new int[][]{{99, 2}})));
        assertNull(deriver.convertMeal(plan(new int[][]{{0, 2}})));
    }

    @Test
    @DisplayName("逐晚不一致（有一晚无餐）：整条 UNKNOWN，不许按多数晚定性")
    void mixedNightsAreUnknown() {
        assertNull(deriver.convertMeal(plan(new int[][]{{2, 2}, {1, 0}})));
    }

    @Test
    @DisplayName("含早却报 0 份、无餐却报有份：矛盾组合一律 UNKNOWN")
    void contradictoryCombosAreUnknown() {
        assertNull(deriver.convertMeal(plan(new int[][]{{2, 0}})));
        assertNull(deriver.convertMeal(plan(new int[][]{{1, 2}})));
    }

    @Test
    @DisplayName("缺逐晚价节点：无从判餐，UNKNOWN")
    void missingPriceListIsUnknown() {
        assertNull(deriver.convertMeal(new DidaRatePlan()));
    }

    /** @param nights 每晚一对 {MealType, MealAmount} */
    private static DidaRatePlan plan(int[][] nights) {
        DidaRatePlan plan = new DidaRatePlan();
        plan.setRatePlanId("test-rate");
        List<DidaPriceItem> prices = new ArrayList<>();
        for (int[] night : nights) {
            DidaPriceItem item = new DidaPriceItem();
            item.setMealType(night[0]);
            item.setMealAmount(night[1]);
            prices.add(item);
        }
        plan.setPriceList(prices);
        return plan;
    }
}
