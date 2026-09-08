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
 * 钉住餐食判据：<b>只认有实证的两种 MealType</b>，其余一律 UNKNOWN。
 *
 * <p>2026-09-08 生产实测 606 条报价出现四种组合（BreakfastType, MealType, MealAmount）：
 * (1,1,0) 344、(2,2,2) 210、(2,3,2) 32、(2,7,2) 20。MealType=7 的样例是日式「1泊2食」
 * （一晚含两餐），而它的 BreakfastType 同样是 2——<b>照 BreakfastType 判就会把早+晚说成
 * 仅含早</b>，那是卖错（R-1.6：赌错只许少卖，不许卖错）。本测试守的就是这条：新值不许被
 * 顺手兜进"含早"。
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
    @DisplayName("MealType=7（一泊二食）不许当成仅含早——无取值表即 UNKNOWN")
    void halfBoardIsUnknownNotBreakfast() {
        assertNull(deriver.convertMeal(plan(new int[][]{{7, 2}})));
    }

    @Test
    @DisplayName("MealType=3 同样无实证，UNKNOWN")
    void unverifiedTypeIsUnknown() {
        assertNull(deriver.convertMeal(plan(new int[][]{{3, 2}})));
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
