package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaCancellationPolicy;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.CancelClassifier;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住退改解析：道旅给的是「<b>从某时刻起</b>罚多少」的起始点列表，本仓要的是分段。
 * 样本取自 2026-09-08 生产真实报文（酒店 563/528，住期 09-29）。
 *
 * <p>翻译规则（官方 price-search 字段说明，2026-09-08 查阅）：FromDate 之前免费；
 * 段 i 的截止时刻 = 下一条的 FromDate；末段无截止（落 before 下限 25，表示"此后一直"）。
 * 时刻一律北京时间——用服务器时区解释会把"还能免费取消多久"说长 8 小时（生产容器跑 UTC）。
 */
class DidaCancelPolicyTest {

    private final DidaProductKeyDeriver deriver = new DidaProductKeyDeriver();

    /** 查价前三周，全部段都还有效 */
    private static final Instant BEFORE_ANY_DEADLINE = Instant.parse("2026-09-08T04:00:00Z");

    @Test
    @DisplayName("阶梯样本：首条 Amount>0 时要补出免费窗，末段落 before=25")
    void ladderGetsLeadingFreeWindow() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-29",
                sample("2026-09-26T00:00:00+08:00", "153", "2026-09-27T00:00:00+08:00", "765"),
                BEFORE_ANY_DEADLINE);

        assertEquals(3, policies.size());
        // 免费窗：截止 09-26 00:00(北京)，距入住日 24:00 = 96 小时
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType());
        assertEquals(96, policies.get(0).getBefore());
        // 09-26 起罚 153，截止 09-27 00:00 = 72 小时
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, policies.get(1).getType());
        assertEquals(153.0, policies.get(1).getValue(), 0.001);
        assertEquals(72, policies.get(1).getBefore());
        // 末段罚全额，"此后一直"
        assertEquals(765.0, policies.get(2).getValue(), 0.001);
        assertEquals(25, policies.get(2).getBefore());

        assertEquals(CancelClass.FREE_CANCELLABLE,
                CancelClassifier.classify(policies, "2026-09-29", 76500, BEFORE_ANY_DEADLINE));
    }

    @Test
    @DisplayName("单条样本（09-25 起罚全额）：免费窗到 09-25，判类为可免费取消")
    void singleEntryStillHasFreeWindow() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-29",
                sample("2026-09-25T00:00:00+08:00", "758"), BEFORE_ANY_DEADLINE);

        assertEquals(2, policies.size());
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType());
        assertEquals(120, policies.get(0).getBefore());
        assertEquals(758.0, policies.get(1).getValue(), 0.001);
        assertEquals(CancelClass.FREE_CANCELLABLE,
                CancelClassifier.classify(policies, "2026-09-29", 75800, BEFORE_ANY_DEADLINE));
    }

    /**
     * 免费窗过期后不许再对外承诺免费——生产实测过这个坑（艺龙 14.3% 的"可免费取消"
     * 免费窗早已关闭）。同一份条款，只是时钟往后走，结论必须翻面。
     */
    @Test
    @DisplayName("免费窗已过：只剩罚金段，判类翻成不可退")
    void expiredFreeWindowFlipsClassToNonRefundable() {
        Instant afterFreeWindow = Instant.parse("2026-09-27T00:00:00Z");
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-29",
                sample("2026-09-26T00:00:00+08:00", "153", "2026-09-27T00:00:00+08:00", "765"),
                afterFreeWindow);

        assertEquals(1, policies.size());
        assertEquals(765.0, policies.get(0).getValue(), 0.001);
        assertEquals(CancelClass.NON_REFUNDABLE,
                CancelClassifier.classify(policies, "2026-09-29", 76500, afterFreeWindow));
    }

    @Test
    @DisplayName("R-5.4：缺字段或时刻不可解析，整条作废而非漏掉一段")
    void brokenEntryVoidsWholeList() {
        DidaCancellationPolicy noAmount = new DidaCancellationPolicy();
        noAmount.setFromDate("2026-09-26T00:00:00+08:00");
        assertTrue(deriver.convertCancelPolicy("2026-09-29", List.of(noAmount), BEFORE_ANY_DEADLINE).isEmpty());

        DidaCancellationPolicy badDate = new DidaCancellationPolicy();
        badDate.setFromDate("not-a-time");
        badDate.setAmount(new BigDecimal("153"));
        assertTrue(deriver.convertCancelPolicy("2026-09-29", List.of(badDate), BEFORE_ANY_DEADLINE).isEmpty());
    }

    @Test
    @DisplayName("政策列表缺席即 UNKNOWN，不许兜成不可退，也不许兜成免费")
    void absentListIsUnknown() {
        assertTrue(deriver.convertCancelPolicy("2026-09-29", null, BEFORE_ANY_DEADLINE).isEmpty());
        assertTrue(deriver.convertCancelPolicy("2026-09-29", List.of(), BEFORE_ANY_DEADLINE).isEmpty());
        assertEquals(CancelClass.UNKNOWN, CancelClassifier.classify(List.of(), "2026-09-29", 75800,
                BEFORE_ANY_DEADLINE));
    }

    /** 乱序也要归位：段的先后由 FromDate 决定，不由数组顺序决定 */
    @Test
    @DisplayName("入参乱序时按 FromDate 排序")
    void sortsByFromDate() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy("2026-09-29",
                sample("2026-09-27T00:00:00+08:00", "765", "2026-09-26T00:00:00+08:00", "153"),
                BEFORE_ANY_DEADLINE);
        assertEquals(153.0, policies.get(1).getValue(), 0.001);
        assertEquals(765.0, policies.get(2).getValue(), 0.001);
    }

    /** @param pairs 交替的 fromDate、amount */
    private static List<DidaCancellationPolicy> sample(String... pairs) {
        return Arrays.asList(java.util.stream.IntStream.range(0, pairs.length / 2)
                .mapToObj(i -> {
                    DidaCancellationPolicy policy = new DidaCancellationPolicy();
                    policy.setFromDate(pairs[i * 2]);
                    policy.setAmount(new BigDecimal(pairs[i * 2 + 1]));
                    return policy;
                })
                .toArray(DidaCancellationPolicy[]::new));
    }
}
