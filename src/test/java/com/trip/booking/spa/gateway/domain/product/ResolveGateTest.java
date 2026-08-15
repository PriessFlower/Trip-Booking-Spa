package com.trip.booking.spa.gateway.domain.product;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 resolve 选票与容差门（docs/product-identity.md R-3.3）。
 *
 * <p>容差门是资损防线：令牌死后自动换票,若不设价格上限,供应商侧涨价会被静默接受、
 * 差额由我方吞掉。宁可 RATE_DEAD 让上游重新报价。
 */
class ResolveGateTest {

    /** 帽取 100000（1000 元）= 远大于用例价位，让既有用例只受比例门约束 */
    private static Optional<Integer> pick(List<Integer> prices, Integer seen, double tolerance) {
        return ResolveGate.pickCheapestWithinTolerance(prices, Integer::intValue, seen, tolerance, 100000);
    }

    private static Optional<Integer> pickCapped(List<Integer> prices, Integer seen, double tolerance, int capCents) {
        return ResolveGate.pickCheapestWithinTolerance(prices, Integer::intValue, seen, tolerance, capCents);
    }

    /** 一个卖法多张在售票是常态（艺龙同卖法 ~14 码），必须选最便宜的 */
    @Test
    void picksCheapestAmongEquivalents() {
        assertEquals(Optional.of(9800), pick(List.of(10100, 9800, 10000), 10000, 0.02));
    }

    /** 最低价也超容差：拒绝换票——防静默涨价成交 */
    @Test
    void refusesWhenCheapestExceedsTolerance() {
        assertTrue(pick(List.of(10300, 10500), 10000, 0.02).isEmpty());
    }

    /** 容差边界恰好命中（10000 × 1.02 = 10200）：放行 */
    @Test
    void boundaryExactlyAtToleranceIsAccepted() {
        assertEquals(Optional.of(10200), pick(List.of(10200), 10000, 0.02));
    }

    /** 略降价当然放行——降价不是资损 */
    @Test
    void cheaperThanSeenIsAccepted() {
        assertEquals(Optional.of(9000), pick(List.of(9000), 10000, 0.0));
    }

    /** 无候选 → empty，调用方回报 RATE_DEAD */
    @Test
    void emptyCandidatesYieldEmpty() {
        assertTrue(pick(List.of(), 10000, 0.02).isEmpty());
        assertTrue(ResolveGate.pickCheapestWithinTolerance(null, Integer::intValue, 10000, 0.02, 100000).isEmpty());
    }

    /** 没有展示价就没有容差基准：一律拒绝，不给"无上限自动换票"留口子 */
    @Test
    void missingOrInvalidSeenPriceRefuses() {
        assertTrue(pick(List.of(9000), null, 0.02).isEmpty());
        assertTrue(pick(List.of(9000), 0, 0.02).isEmpty());
        assertTrue(pick(List.of(9000), -1, 0.02).isEmpty());
    }

    /**
     * 绝对帽在大额单上取代比例门（issue #59）：5 万元单 2% = 1000 元，
     * 无帽会被放行——帽 50 元把单笔自动让利钉死。
     */
    @Test
    void absoluteCapBindsOnLargeOrders() {
        // 展示价 5_000_000 分（5 万元），帽 5000 分（50 元）：+50 元放行，+51 元拒绝
        assertEquals(Optional.of(5005000), pickCapped(List.of(5005000), 5000000, 0.02, 5000));
        assertTrue(pickCapped(List.of(5005100), 5000000, 0.02, 5000).isEmpty());
    }

    /** 小额单仍由比例门主导：帽远大于比例额度时行为与单门时代完全一致 */
    @Test
    void ratioStillBindsOnSmallOrders() {
        // 展示价 10000 分：比例额度 200 分 < 帽 5000 分 → 10200 放行、10201 拒绝
        assertEquals(Optional.of(10200), pickCapped(List.of(10200), 10000, 0.02, 5000));
        assertTrue(pickCapped(List.of(10201), 10000, 0.02, 5000).isEmpty());
    }

    /** 帽=0 即严格等价（不许任何涨价）；负帽按 0 处理，不产生"负容差"怪态 */
    @Test
    void zeroOrNegativeCapMeansStrictEquality() {
        assertEquals(Optional.of(10000), pickCapped(List.of(10000), 10000, 0.02, 0));
        assertTrue(pickCapped(List.of(10001), 10000, 0.02, 0).isEmpty());
        assertEquals(Optional.of(9900), pickCapped(List.of(9900), 10000, 0.02, -1));
    }
}
