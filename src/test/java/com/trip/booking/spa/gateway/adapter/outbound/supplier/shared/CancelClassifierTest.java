package com.trip.booking.spa.gateway.adapter.outbound.supplier.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退改判类的唯一判据，重点钉「过期段不算」。
 *
 * <p>数据取自 2026-09-02 生产实测：艺龙店 11957937 入住 09-03、免费段 {@code before=78}
 * （→ 免费截止 08-31 18:00），读到时已是 09-02 15:59——旧判据照样报"可免费取消"，
 * 而此刻取消实际要罚。抽样 659 条免费产品里 94 条（14.3%）是这个形态。
 */
class CancelClassifierTest {

    private static final ZoneId BJ = ZoneId.of("Asia/Shanghai");

    private static Instant at(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(BJ).toInstant();
    }

    private static CancelPolicy seg(int before, RefundType type, Integer amountCents) {
        return CancelPolicy.builder().cancelType(1).timeZone("GMT+08:00")
                .before(before).type(type).amount(amountCents).build();
    }

    @Test
    @DisplayName("免费窗已过期：不许再判可免费取消（生产 11957937 的真实形态）")
    void expiredFreeWindowIsNotFreeCancellable() {
        // 入住 09-03，免费段 before=78h → 截止 08-31 18:00；罚金段 before=25h → 截止 09-02 23:00
        List<CancelPolicy> policies = List.of(
                seg(78, RefundType.NO_DEDUCTION, 0),
                seg(25, RefundType.DEDUCT_BY_AMOUNT, 43300));

        CancelClass now = CancelClassifier.classify(policies, "2026-09-03", 43300,
                at("2026-09-02T15:59:00"));

        assertEquals(CancelClass.NON_REFUNDABLE, now,
                "免费窗 08-31 18:00 就关了，09-02 再判可免费取消=对外承诺一个订不到的免费退");
    }

    @Test
    @DisplayName("同一免费段在窗内照常判可免费取消——不许把有效窗一起滤掉")
    void liveFreeWindowStaysFree() {
        List<CancelPolicy> policies = List.of(
                seg(78, RefundType.NO_DEDUCTION, 0),
                seg(25, RefundType.DEDUCT_BY_AMOUNT, 43300));

        CancelClass early = CancelClassifier.classify(policies, "2026-09-03", 43300,
                at("2026-08-30T10:00:00"));

        assertEquals(CancelClass.FREE_CANCELLABLE, early, "08-30 时免费窗还开着（截止 08-31 18:00）");
    }

    @Test
    @DisplayName("相邻两晚判成同一类：过期免费段是 productKey 分裂的来源")
    void adjacentNightsClassifyTheSame() {
        // 供应商在 09-02 那晚多回了一条已作废的免费段，09-03 那晚没回——旧判据一个 FREE 一个不是，
        // productKey 随之分裂，多晚查询凑不齐每一天（day_count_mismatch）
        List<CancelPolicy> night1 = List.of(
                seg(145, RefundType.NO_DEDUCTION, 0),
                seg(25, RefundType.DEDUCT_BY_AMOUNT, 4330));
        List<CancelPolicy> night2 = List.of(seg(25, RefundType.DEDUCT_BY_AMOUNT, 3499));
        Instant now = at("2026-09-02T15:59:00");

        assertEquals(
                CancelClassifier.classify(night2, "2026-09-04", 3499, now),
                CancelClassifier.classify(night1, "2026-09-03", 4330, now),
                "两晚都是「只剩罚全款」，过期免费段不该把它们判成两种卖法");
    }

    @Test
    @DisplayName("出报侧同样滤：已过期的段不许流给上游")
    void expiredSegmentsAreNotHandedOut() {
        List<CancelPolicy> policies = List.of(
                seg(78, RefundType.NO_DEDUCTION, 0),
                seg(25, RefundType.DEDUCT_BY_AMOUNT, 43300));

        List<CancelPolicy> live = CancelClassifier.liveSegments(policies, "2026-09-03",
                at("2026-09-02T15:59:00"));

        assertEquals(1, live.size(), "只该留下罚金段");
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, live.get(0).getType());
    }

    @Test
    @DisplayName("没有入住日或没有时钟：原样返回，不许误删有效段")
    void withoutCheckInOrClockNothingIsDropped() {
        List<CancelPolicy> policies = List.of(seg(78, RefundType.NO_DEDUCTION, 0));

        assertEquals(1, CancelClassifier.liveSegments(policies, null, Instant.now()).size());
        assertEquals(1, CancelClassifier.liveSegments(policies, "2026-09-03", null).size());
        assertEquals(CancelClass.FREE_CANCELLABLE,
                CancelClassifier.classify(policies, null, null, null),
                "判不了过期时行为与旧判据一致，避免改动本身造成静默少卖");
    }

    @Test
    @DisplayName("全部段都已作废：判不确定，不许猜成免费或不可退")
    void allSegmentsExpiredIsUnknown() {
        List<CancelPolicy> policies = List.of(seg(78, RefundType.NO_DEDUCTION, 0));

        assertEquals(CancelClass.UNKNOWN,
                CancelClassifier.classify(policies, "2026-09-03", 43300, at("2026-09-02T15:59:00")));
    }

    @Test
    @DisplayName("时区缺失回落北京时间——按服务器 UTC 算会把免费窗说长 8 小时")
    void missingTimeZoneFallsBackToBeijing() {
        CancelPolicy noTz = CancelPolicy.builder().cancelType(1)
                .before(25).type(RefundType.NO_DEDUCTION).amount(0).build();

        // 入住 09-03 → 北京时间截止 09-02 23:00；若按 UTC 算截止会变成 09-03 07:00(北京)
        assertTrue(CancelClassifier.liveSegments(List.of(noTz), "2026-09-03",
                at("2026-09-02T22:00:00")).size() == 1, "22:00 尚在窗内");
        assertTrue(CancelClassifier.liveSegments(List.of(noTz), "2026-09-03",
                at("2026-09-03T00:30:00")).isEmpty(), "23:00 已过，按 UTC 算会误判为仍有效");
    }
}
