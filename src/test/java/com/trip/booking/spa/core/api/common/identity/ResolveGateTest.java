package com.trip.booking.spa.core.api.common.identity;

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

    private static Optional<Integer> pick(List<Integer> prices, Integer seen, double tolerance) {
        return ResolveGate.pickCheapestWithinTolerance(prices, Integer::intValue, seen, tolerance);
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
        assertTrue(ResolveGate.pickCheapestWithinTolerance(null, Integer::intValue, 10000, 0.02).isEmpty());
    }

    /** 没有展示价就没有容差基准：一律拒绝，不给"无上限自动换票"留口子 */
    @Test
    void missingOrInvalidSeenPriceRefuses() {
        assertTrue(pick(List.of(9000), null, 0.02).isEmpty());
        assertTrue(pick(List.of(9000), 0, 0.02).isEmpty());
        assertTrue(pick(List.of(9000), -1, 0.02).isEmpty());
    }
}
