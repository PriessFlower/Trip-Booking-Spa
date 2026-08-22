package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 艺龙餐食文案归类。用例文本<b>全部取自生产实测</b>（2026-08-19，828 家酒店 30,243 条报价，
 * 归一份数后共 19 种取值），不是构造出来的。
 *
 * <p>守护的是一条会<b>卖错</b>的路径：餐食是 productKey 的成分，判错就会让不同卖法合流——
 * 「到店三选二」并进「无餐食」、「全餐」并进「仅含早」。裁剪会按等价类留最低价把含餐多的
 * 那条裁掉（F-3.2），resolve 会拿无餐票换含餐票（R-3.2）。
 */
class ElongMealTextTest {

    private final ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
    private final ObjectMapper mapper = new ObjectMapper();

    private Meal convert(String copyWriting) {
        ElongRatePlan plan = new ElongRatePlan();
        plan.setRatePlanId(1L);
        if (copyWriting != null) {
            ObjectNode meals = mapper.createObjectNode();
            meals.put("mealText", copyWriting);
            plan.setMeals(meals);
        }
        return deriver.convertMeal(plan);
    }

    // ── ① 选择型：必须 UNKNOWN（本测试最重要的一组） ──────────────────────

    @Test
    @DisplayName("到店协商的选择型餐食一律 UNKNOWN——订时确定不了给哪几顿")
    void indeterminateMealsAreUnknown() {
        String[] production = {
                "1份早餐或1份午餐或1份晚餐(到店3选2,请自行与酒店协商)",
                "2份早餐或2份午餐或2份晚餐(到店3选2,请自行与酒店协商)",
                "3份早餐或3份午餐或3份晚餐(到店3选2,请自行与酒店协商)",
                "4份早餐或4份午餐或4份晚餐(到店3选2,请自行与酒店协商)",
                "1份早餐+1份午餐或1份晚餐(到店2选1,请自行与酒店协商)",
                "1份早餐或1份晚餐(到店2选1,请自行与酒店协商)",
                "1份早餐或1份午餐(到店2选1,请自行与酒店协商)",
        };
        for (String copy : production) {
            assertNull(convert(copy),
                    "选择型餐食必须判 UNKNOWN，否则会与「无餐食」或「仅含早」合流：" + copy);
        }
    }

    @Test
    @DisplayName("选择型判定必须先于含餐名判定——顺序反了就会把三选二读成确定含早")
    void choiceWordingWinsOverMealNames() {
        // 这条同时含「早餐」「午餐」「晚餐」三个词，若先按含餐名归类会误判成全餐
        assertNull(convert("1份早餐或1份午餐或1份晚餐(到店3选2,请自行与酒店协商)"),
                "含「或」的文案不得被读成确定餐食组合——这是本类唯一会造成卖错的方向");
    }

    // ── ② 确定型 ────────────────────────────────────────────────────

    @Test
    @DisplayName("供应商正面声明「无餐食」是可信的确定信息，不是没填")
    void explicitNoMeal() {
        Meal meal = convert("无餐食");
        assertNotNull(meal, "「无餐食」是正面声明，应判为确定的无餐而非 UNKNOWN");
        assertEquals(0, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(0, meal.getDinnerCount());
    }

    @Test
    @DisplayName("N份早餐 → 仅含早，份数取文案值")
    void breakfastOnly() {
        for (int n : new int[]{1, 2, 3, 4, 5}) {
            Meal meal = convert(n + "份早餐");
            assertNotNull(meal, n + "份早餐 应可归类");
            assertEquals(n, meal.getCount(), "早餐份数应取文案里的 N");
            assertEquals(0, meal.getLunchCount());
            assertEquals(0, meal.getDinnerCount());
        }
    }

    @Test
    @DisplayName("全餐必须判出午晚餐——旧实现把 lunch/dinner 写死 0，正是它让全餐并进了仅含早")
    void fullBoardKeepsLunchAndDinner() {
        for (String copy : new String[]{
                "1份早餐和1份午餐和1份晚餐",
                "1份早餐和1份午餐和1份晚餐和小食饮料",
                "2份早餐和2份午餐和2份晚餐和小食饮料",
                "3份早餐和3份午餐和3份晚餐和小食饮料"}) {
            Meal meal = convert(copy);
            assertNotNull(meal, copy + " 应可归类");
            assertTrue(meal.getCount() > 0, "全餐应含早：" + copy);
            assertTrue(meal.getLunchCount() > 0, "全餐的午餐不得为 0：" + copy);
            assertTrue(meal.getDinnerCount() > 0, "全餐的晚餐不得为 0：" + copy);
        }
    }

    @Test
    @DisplayName("早+晚（无午餐）应如实判出，不得当成仅含早")
    void breakfastAndDinner() {
        Meal meal = convert("1份早餐和1份晚餐");
        assertNotNull(meal);
        assertTrue(meal.getCount() > 0);
        assertEquals(0, meal.getLunchCount(), "文案没提午餐，不得臆造");
        assertTrue(meal.getDinnerCount() > 0, "晚餐必须判出");
    }

    // ── ③ 兜底：不认识就 UNKNOWN，绝不猜 ─────────────────────────────

    @Test
    @DisplayName("文案缺席 → UNKNOWN：午晚餐无从判断，不可假定为无")
    void missingCopyWritingIsUnknown() {
        assertNull(convert(null));
        assertNull(convert(""));
        assertNull(convert("   "));
    }

    @Test
    @DisplayName("表外文案 → UNKNOWN（沿用 Expedia 2203 的判据：猜错是卖错，不归类只是少卖）")
    void unrecognizedCopyWritingIsUnknown() {
        assertNull(convert("含下午茶"), "未见过的餐食措辞不得臆断");
        assertNull(convert("1份午餐"), "仅午餐生产未见，不臆断");
        assertNull(convert("beverage package"), "不含任何三餐词的文案应判 UNKNOWN");
    }

    // ── ④ 合流守护：不同餐食形态必须落到不同的餐食签名 ──────────────────

    @Test
    @DisplayName("守护：三选二不得与无餐食合流，全餐不得与仅含早合流")
    void distinctShapesMustNotCollapse() {
        Meal noMeal = convert("无餐食");
        Meal breakfast = convert("1份早餐");
        Meal fullBoard = convert("1份早餐和1份午餐和1份晚餐");
        Meal choice = convert("1份早餐或1份午餐或1份晚餐(到店3选2,请自行与酒店协商)");

        assertNull(choice, "三选二应为 UNKNOWN（与「无餐食」不同的键值）");
        assertNotNull(noMeal);
        assertNotNull(breakfast);
        assertNotNull(fullBoard);

        assertTrue(signature(fullBoard) != signature(breakfast),
                "全餐与仅含早必须落到不同签名，否则裁剪会把全餐当同一卖法裁掉(F-3.2)");
        assertTrue(signature(noMeal) != signature(breakfast),
                "无餐与含早必须落到不同签名");
    }

    /** 用与 productKey 相同的口径（只看有无，不看份数）压成一个可比较的位 */
    private static int signature(Meal meal) {
        return (positive(meal.getCount()) ? 4 : 0)
                + (positive(meal.getLunchCount()) ? 2 : 0)
                + (positive(meal.getDinnerCount()) ? 1 : 0);
    }

    private static boolean positive(Integer v) {
        return v != null && v > 0;
    }
}
