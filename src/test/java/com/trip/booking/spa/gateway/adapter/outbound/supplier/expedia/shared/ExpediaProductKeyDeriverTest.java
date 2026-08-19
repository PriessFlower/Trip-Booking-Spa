package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 餐食规范化与键派生的判据钉死（docs/product-identity.md R-1.1、docs/price-refresh.md F-3.2）。
 *
 * <p>核心命题：<b>含早与无早、全餐与仅含早，必须落在不同的 productKey 等价类里</b>。
 * 曾经因为 amenity 清单漏写逗号（{@code 2102,2103} → {@code 21022103}）使两个 case 变成
 * 死代码，含早被判无早；又因为"清单里位置靠后者胜"，全餐 rate 被排在后面的免费早餐盖掉，
 * 午晚餐丢失（issue #97）。
 */
class ExpediaProductKeyDeriverTest {

    /** 生产实测的真实 id 与名称（expedia_property_content.raw_json，2026-08-19 抽样 300 家） */
    private static final String FREE_BREAKFAST = "1073742786";   // 免费早餐，838 次
    private static final String BREAKFAST_FOR_TWO = "2194";      // 双早，143 次
    private static final String BREAKFAST_BUFFET = "2205";       // 自助早餐，120 次
    private static final String FULL_BOARD = "2102";             // 全餐，56 次
    private static final String BREAKFAST_FOR_ONE = "1073742857";// 单早，32 次
    private static final String CONTINENTAL = "2103";            // 欧式早餐，12 次
    private static final String UNKNOWN_2203 = "2203";           // 抽样中 0 次，有意未收录

    private ExpediaProductKeyDeriver deriver;

    @BeforeEach
    void setUp() {
        deriver = new ExpediaProductKeyDeriver();
        ExpediaContractProfile profile = Mockito.mock(ExpediaContractProfile.class);
        Mockito.when(profile.getPartnerPointOfSale()).thenReturn("TEST_PPOS");
        deriver.setContractProfile(profile);
    }

    private static Map<String, QueryPriceResponse.Amenity> amenities(String... ids) {
        Map<String, QueryPriceResponse.Amenity> map = new LinkedHashMap<>();
        for (String id : ids) {
            QueryPriceResponse.Amenity a = new QueryPriceResponse.Amenity();
            a.setId(id);
            a.setName("name-" + id);
            map.put(id, a);
        }
        return map;
    }

    /**
     * 本类存在的首要理由：**清单里每个 id 都必须真的被归类**。
     * 这条断言本身就能防住下一次漏逗号——漏写会产出 {@code 21022103} 这种拼接 token，
     * 它不在映射里，于是 2102/2103 的用例立刻转红。
     */
    @Test
    void everyRegisteredAmenityMapsToAnActualMeal() {
        Map<String, ?> registry = ExpediaProductKeyDeriver.mealAmenitiesForTest();
        assertFalse(registry.isEmpty(), "餐食 amenity 映射不得为空");
        registry.keySet().forEach(id -> {
            assertTrue(id.matches("\\d+"), "amenity id 必须是纯数字，出现 " + id + " 说明有拼接或笔误");
            Meal meal = deriver.convertMeal(2, amenities(id));
            int total = meal.getCount() + meal.getLunchCount() + meal.getDinnerCount();
            assertTrue(total > 0, "已登记的 amenity " + id + " 竟被判为无餐食——归类漏了");
        });
    }

    /** 漏逗号的直接回归：拼接 token 不得存在，2102 与 2103 必须各自成键 */
    @Test
    void mealAmenityIdsAreNotConcatenated() {
        Map<String, ?> registry = ExpediaProductKeyDeriver.mealAmenitiesForTest();
        assertFalse(registry.containsKey("21022103"), "2102 与 2103 又被拼成了一个 token");
        assertTrue(registry.containsKey(FULL_BOARD));
        assertTrue(registry.containsKey(CONTINENTAL));
    }

    @Test
    void breakfastForOneReportsExactlyOnePortion() {
        Meal meal = deriver.convertMeal(3, amenities(BREAKFAST_FOR_ONE));
        assertEquals(1, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(0, meal.getDinnerCount());
    }

    @Test
    void breakfastForTwoIsCappedByGuestCount() {
        assertEquals(2, deriver.convertMeal(4, amenities(BREAKFAST_FOR_TWO)).getCount());
        assertEquals(1, deriver.convertMeal(1, amenities(BREAKFAST_FOR_TWO)).getCount());
    }

    /** 修复前 2102 是死 case，全餐落 default 判无餐食 */
    @Test
    void fullBoardCoversAllThreeMeals() {
        Meal meal = deriver.convertMeal(2, amenities(FULL_BOARD));
        assertEquals(2, meal.getCount());
        assertEquals(2, meal.getLunchCount());
        assertEquals(2, meal.getDinnerCount());
    }

    /** 修复前 2103 是死 case，欧式早餐被判无早——生产抽样中单独出现 6 次 */
    @Test
    void continentalBreakfastCountsAsBreakfast() {
        Meal meal = deriver.convertMeal(2, amenities(CONTINENTAL));
        assertEquals(2, meal.getCount());
        assertEquals(0, meal.getLunchCount());
    }

    /**
     * 生产实测最高频的错判：免费早餐与全餐共现 56 次，旧实现按清单顺序取到免费早餐，
     * 午晚餐丢失。合并规则改为取并集后应三餐齐全。
     */
    @Test
    void mergesCoOccurringAmenitiesAsUnionOfMeals() {
        Meal meal = deriver.convertMeal(2, amenities(FREE_BREAKFAST, FULL_BOARD));
        assertEquals(2, meal.getCount());
        assertEquals(2, meal.getLunchCount(), "全餐与免费早餐共现时不得丢掉午餐");
        assertEquals(2, meal.getDinnerCount(), "全餐与免费早餐共现时不得丢掉晚餐");
        assertEquals("name-" + FULL_BOARD, meal.getMealDesc(), "描述应取覆盖餐种最多的那条");
    }

    /** 份数取保守侧（R-1.6）：单早与自助早餐共现 28 次，报 1 份而非入住人数 */
    @Test
    void takesTheMostConservativePortionCountWhenAmenitiesDisagree() {
        Meal meal = deriver.convertMeal(3, amenities(BREAKFAST_FOR_ONE, BREAKFAST_BUFFET));
        assertEquals(1, meal.getCount());
    }

    /** 2203 语义未确证，有意不收录：宁可少报餐食，也不把无餐食房报成含早（R-1.6） */
    @Test
    void unregisteredAmenityFallsBackToNoMeal() {
        Meal meal = deriver.convertMeal(2, amenities(UNKNOWN_2203));
        assertEquals(0, meal.getCount());
        assertEquals("", meal.getMealDesc());
    }

    @Test
    void missingAmenitiesMeansNoMealWithZeroNotNull() {
        Meal meal = deriver.convertMeal(2, null);
        assertEquals(0, meal.getCount());
        assertEquals(0, meal.getLunchCount());
        assertEquals(0, meal.getDinnerCount());
    }

    /**
     * 键层面的净结果（R-3.2）：含早与无早同房型不得同键，否则 resolve 可能拿无早票换含早票；
     * 全餐与仅含早也不得同键，否则裁剪时更贵的全餐那条会被当成同一卖法裁掉（F-3.2）。
     */
    @Test
    void breakfastVariantsDeriveDistinctProductKeys() {
        List<CancelPolicy> free = List.of(CancelPolicy.builder().cancelType(1).build());

        String noMeal = key(deriver.convertMeal(2, amenities(UNKNOWN_2203)), free);
        String breakfast = key(deriver.convertMeal(2, amenities(FREE_BREAKFAST)), free);
        String fullBoard = key(deriver.convertMeal(2, amenities(FULL_BOARD)), free);

        assertNotEquals(noMeal, breakfast, "含早与无早必须分键");
        assertNotEquals(breakfast, fullBoard, "全餐与仅含早必须分键");
        assertNotEquals(noMeal, fullBoard);
    }

    /** 共现顺序不得影响键：等价类不能因为供应商下发顺序而漂移 */
    @Test
    void productKeyIsIndependentOfAmenityOrder() {
        List<CancelPolicy> free = List.of(CancelPolicy.builder().cancelType(1).build());
        String a = key(deriver.convertMeal(2, amenities(FREE_BREAKFAST, FULL_BOARD)), free);
        String b = key(deriver.convertMeal(2, amenities(FULL_BOARD, FREE_BREAKFAST)), free);
        assertEquals(a, b);
    }

    private String key(Meal meal, List<CancelPolicy> cancelPolicy) {
        return deriver.deriveProductKey("H1", "R1", meal, cancelPolicy, "2");
    }
}
