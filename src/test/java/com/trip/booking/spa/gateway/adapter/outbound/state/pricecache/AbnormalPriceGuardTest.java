package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常价拦截的判据钉死（docs/price-refresh.md F-7，issue #70）。
 *
 * <p>默认阈值：跌幅 > 50% 且旧价 ≥ 100 元才拦。两个数都取自艺龙生产在售价
 * 64,918 条的实测分布，改动须同步 price-refresh.md 的证据段。
 */
class AbnormalPriceGuardTest {

    private AbnormalPriceGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AbnormalPriceGuard();
        ReflectionTestUtils.setField(guard, "dropRatio", 0.5);
        ReflectionTestUtils.setField(guard, "floorCents", 10000);
    }

    /** 本闸要防的核心场景：800 元的房返回 8 元（供应商数据错或我方解析错单位） */
    @Test
    void blocksTheCatastrophicDrop() {
        assertTrue(guard.isAbnormalDrop(80000, 800));
    }

    /** 恰好腰斩不拦（阈值是"超过"而非"达到"），略超才拦 */
    @Test
    void thresholdIsStrictlyGreaterThan() {
        assertFalse(guard.isAbnormalDrop(80000, 40000));   // 正好 50%
        assertTrue(guard.isAbnormalDrop(80000, 39999));    // 略超 50%
    }

    /**
     * 真实促销必须放行。艺龙常见 20~30% 折扣，阈值定低会大量误拦——
     * 客人看不到优惠，且事后难以解释优惠为何消失。
     */
    @Test
    void allowsGenuinePromotions() {
        assertFalse(guard.isAbnormalDrop(80000, 64000));   // -20%
        assertFalse(guard.isAbnormalDrop(80000, 56000));   // -30%
        assertFalse(guard.isAbnormalDrop(80000, 48000));   // -40%
    }

    /** 只防跌不防涨：涨价的后果是少卖，不是卖错（R-1.6） */
    @Test
    void neverBlocksPriceIncrease() {
        assertFalse(guard.isAbnormalDrop(10000, 999999));
        assertFalse(guard.isAbnormalDrop(10000, 10000));
    }

    /**
     * 无基准一律放行。拦掉首刷会让新产品永远进不了缓存——
     * "没有依据"不等于"应当拦截"。
     */
    @Test
    void allowsWhenNoBaseline() {
        assertFalse(guard.isAbnormalDrop(null, 100));
        assertFalse(guard.isAbnormalDrop(0, 100));
        assertFalse(guard.isAbnormalDrop(-1, 100));
    }

    /** 低价房不受保护：波动大且绝对损失有限，误判成本高于放行 */
    @Test
    void skipsLowPricedBaseline() {
        assertFalse(guard.isAbnormalDrop(9999, 1));        // 旧价 99.99 元 < 下限
        assertTrue(guard.isAbnormalDrop(10000, 1));        // 旧价恰好 100 元 → 受保护
    }

    /** 新价非法（null / 非正）时放行，交由上游的空值处置，不在本闸伪装成"异常价" */
    @Test
    void allowsWhenNewPriceInvalid() {
        assertFalse(guard.isAbnormalDrop(80000, null));
        assertFalse(guard.isAbnormalDrop(80000, 0));
    }

    /** 阈值可配：调成 0.8 后，原本被拦的 -60% 应放行 */
    @Test
    void thresholdIsConfigurable() {
        assertTrue(guard.isAbnormalDrop(100000, 40000));   // -60%，默认 0.5 下拦截
        ReflectionTestUtils.setField(guard, "dropRatio", 0.8);
        assertFalse(guard.isAbnormalDrop(100000, 40000));  // 阈值放宽后放行
    }
}
