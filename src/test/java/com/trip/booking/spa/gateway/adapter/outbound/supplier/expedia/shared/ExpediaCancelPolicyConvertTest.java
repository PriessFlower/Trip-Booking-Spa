package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expedia 退改规范化的判据钉死（R-1.6/R-5.4）。报文形态全部取自 2026-08-28 对
 * test.ean.com 的实测采样（210 家 × T+1/T+13/T+30，2,154 条含罚金 rate，
 * 见 docs/expedia/cancel-penalties.md）——生产凭据只在 test.ean.com 有效，
 * 线上消费的就是这份形态。
 *
 * <p>核心命题：<b>罚金窗已开（start ≤ 当下）的 rate 不得说成可免费取消</b>。
 * 采样中该形态占含罚金 rate 的 40%（T+1 住期 70%），全部伴随 refundable=false；
 * 旧实现只要 cancel_penalties 存在就垫一段免费头段，把它们全数标成
 * FREE_CANCELLABLE——旅客据此取消要挨全款罚金，方向与艺龙 26,011 事故相反且更糟。
 */
class ExpediaCancelPolicyConvertTest {

    /** 采样固定参照时刻：2026-08-28 12:00 +08:00 */
    private static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");

    private ExpediaProductKeyDeriver deriver;

    @BeforeEach
    void setUp() {
        deriver = new ExpediaProductKeyDeriver();
        ExpediaContractProfile profile = Mockito.mock(ExpediaContractProfile.class);
        Mockito.when(profile.getPartnerPointOfSale()).thenReturn("TEST_PPOS");
        deriver.setContractProfile(profile);
        ReflectionTestUtils.setField(deriver, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static List<QueryPriceResponse.CancelPolicy> penalties(String rawJson) {
        return JsonUtils.decodeJson(rawJson, new TypeReference<List<QueryPriceResponse.CancelPolicy>>() {
        });
    }

    private static Meal noMeal() {
        return Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
    }

    private String classify(List<CancelPolicy> cancelPolicy) {
        return deriver.deriveIdentity("H1", "R1", noMeal(), cancelPolicy, "2").cancelClass();
    }

    /**
     * 采样最高频的过度承诺形态（T+1 住期 230/457 条）：罚金窗 7 月 19 日已开、
     * 罚全款、refundable=false。不得垫免费头段，且 100% 段必须保留。
     */
    @Test
    void startedFullPricePenaltyMustNotFabricateFreeWindow() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", penalties(
                "[{\"start\":\"2026-07-19T03:00:00.000+08:00\",\"end\":\"2026-08-29T18:00:00.000+08:00\","
                        + "\"percent\":\"100%\",\"currency\":\"CNY\"}]"));

        assertFalse(policies.stream().anyMatch(p -> RefundType.NO_DEDUCTION == p.getType()),
                "罚金窗已开还垫免费头段=把不能免费退说成能退");
        assertEquals(1, policies.size());
        assertEquals(RefundType.DEDUCT_BY_PERCENT, policies.get(0).getType(), "罚全款段不得丢弃");
        assertEquals(100D, policies.get(0).getValue());
        assertEquals(CancelClass.NON_REFUNDABLE.name(), classify(policies),
                "全程罚全款=经济上不可退，绝不是 FREE_CANCELLABLE");
    }

    /** 罚金窗尚未开：免费头段是真的，截止=罚金窗 start；100% 罚金段照样保留 */
    @Test
    void futureFreeWindowKeepsHeadAndFullPenaltyTail() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-12", penalties(
                "[{\"start\":\"2026-09-10T18:00:00.000+08:00\",\"end\":\"2026-09-12T18:00:00.000+08:00\","
                        + "\"percent\":\"100%\",\"currency\":\"CNY\"}]"));

        assertEquals(2, policies.size());
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType());
        // 头段截止=罚金窗 start（09-10 18:00 +08）距入住日 24:00（09-13 00:00 +08）= 54 小时
        assertEquals(54, policies.get(0).getBefore());
        assertEquals("GMT+08:00", policies.get(0).getTimeZone());
        assertEquals(RefundType.DEDUCT_BY_PERCENT, policies.get(1).getType(), "旧实现把 100% 段整个丢弃");
        assertEquals(100D, policies.get(1).getValue());
        assertEquals(25, policies.get(1).getBefore(), "段截止在入住日内，before 收下限 25");
        assertEquals(CancelClass.FREE_CANCELLABLE.name(), classify(policies));
    }

    /**
     * 罚金窗已开、按晚扣（T+1 住期实测 92 条，refundable=false）：没有免费窗，
     * 但按晚是否等于全款判不出——按 R-1.6 归 UNKNOWN，不得说成 FREE 也不得赌成不可退。
     */
    @Test
    void startedNightsPenaltyClassifiesUnknownNotFree() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", penalties(
                "[{\"start\":\"2026-08-22T18:00:00.000+08:00\",\"end\":\"2026-08-29T18:00:00.000+08:00\","
                        + "\"nights\":\"1\",\"currency\":\"CNY\"}]"));

        assertEquals(1, policies.size());
        assertEquals(RefundType.DEDUCT_DAY_NIGHT, policies.get(0).getType());
        assertEquals(1D, policies.get(0).getValue());
        assertEquals(CancelClass.UNKNOWN.name(), classify(policies));
        assertFalse(deriver.isCatalogEligible(noMeal(), policies), "UNKNOWN 可售但不得进目录（R-5.4）");
    }

    /** 采样真实多段阶梯（50% → 100%，窗已开）：每段都要转出，不得只取最早一段 */
    @Test
    void multiSegmentLadderKeepsEverySegment() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", penalties(
                "[{\"start\":\"2026-08-27T23:59:00.000+09:00\",\"end\":\"2026-08-28T23:59:00.000+09:00\","
                        + "\"percent\":\"50%\",\"currency\":\"CNY\"},"
                        + "{\"start\":\"2026-08-28T23:59:00.000+09:00\",\"end\":\"2026-08-29T23:59:00.000+09:00\","
                        + "\"percent\":\"100%\",\"currency\":\"CNY\"}]"));

        assertEquals(2, policies.size(), "旧实现只转 start 最早的一段，其余整段丢弃");
        assertEquals(50D, policies.get(0).getValue());
        assertEquals(100D, policies.get(1).getValue());
        assertFalse(policies.stream().anyMatch(p -> RefundType.NO_DEDUCTION == p.getType()));
        // 50% 段判不出是否全款 → 三分类无处安放，UNKNOWN（可售不进目录）
        assertEquals(CancelClass.UNKNOWN.name(), classify(policies));
    }

    /** 罚金载体不认识（无 amount/percent/nights）：UNKNOWN，不得兜成不可退（R-5.4） */
    @Test
    void unknownPenaltyCarrierIsUnknownNotNonRefundable() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", penalties(
                "[{\"start\":\"2026-08-22T18:00:00.000+08:00\",\"end\":\"2026-08-29T18:00:00.000+08:00\","
                        + "\"currency\":\"CNY\"}]"));

        assertTrue(policies.isEmpty(), "不确定不许说成确定：解析不出=空列表");
        assertEquals(CancelClass.UNKNOWN.name(), classify(policies));
    }

    /** 段时间解析不出：整体 UNKNOWN，禁止拿垃圾 before 继续（R-5.4） */
    @Test
    void unparsableSegmentTimeIsUnknown() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", penalties(
                "[{\"start\":\"not-a-date\",\"end\":\"2026-08-29T18:00:00.000+08:00\","
                        + "\"percent\":\"100%\",\"currency\":\"CNY\"}]"));

        assertTrue(policies.isEmpty());
    }

    /** cancel_penalties 缺席维持旧口径：不可退（采样 2,154 条中未出现，另行实证前不动） */
    @Test
    void missingPenaltiesStaysNonRefundable() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-08-29", null);

        assertEquals(1, policies.size());
        assertEquals(0, policies.get(0).getCancelType());
        assertEquals(CancelClass.NON_REFUNDABLE.name(), classify(policies));
    }

    /** nights="0" 段=窗内罚 0 晚，是真免费窗：照旧记 NO_DEDUCTION、截止取段 end */
    @Test
    void zeroNightsSegmentIsARealFreeWindow() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-12", penalties(
                "[{\"start\":\"2026-08-22T18:00:00.000+08:00\",\"end\":\"2026-09-10T18:00:00.000+08:00\","
                        + "\"nights\":\"0\",\"currency\":\"CNY\"}]"));

        assertEquals(1, policies.size());
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType());
        assertEquals(54, policies.get(0).getBefore());
        assertEquals(CancelClass.FREE_CANCELLABLE.name(), classify(policies));
    }
}
