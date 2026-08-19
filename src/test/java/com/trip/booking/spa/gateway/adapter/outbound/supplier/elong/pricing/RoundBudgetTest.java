package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 2026-08-19 的生产缺陷：轮次预算只按限流配额估时，隐含假设"总能跑到配置的 QPS"。
 *
 * <p>实际速率是 {@code min(配额, 并发度 / 单行耗时)}。常规档配额 4 QPS、并发 3，实测只跑出
 * 1.82 QPS，而预算按 4 QPS 给成 220s，于是连续三轮精确卡在 220s 被 {@code shutdownNow} 截断，
 * <b>每轮丢 166/400 = 41% 的行</b>——那些行本轮没有刷价。三态计数之和只有 234，与"共 400 行"
 * 不符，但当时的日志没有把差额算出来，读起来像是全都处理了。
 *
 * <p>本测试用<b>当时的真实取值</b>做反证：把 concurrency 传 3，断言预算必须大于实测所需的 220s。
 */
class RoundBudgetTest {

    @Test
    @DisplayName("并发跑不满配额时，预算必须按并发能力估，而不是按配额")
    void budgetFollowsTheSlowerOfQuotaAndConcurrency() {
        // 事故当时的取值：400 行、配额 4 QPS、并发 3 → 实测 1.82 QPS，耗时 220s 才勉强跑完
        long budget = ElongCPSQueryPriceServiceImpl.roundBudgetSeconds(400, 4.0, 3);

        assertTrue(budget > 220,
                "预算 " + budget + "s 不足。事故当时 400 行/并发 3 实测需要 220s 以上，"
                        + "而按配额 4 QPS 算只给 220s，导致每轮被截断、丢 41% 的行");
    }

    @Test
    @DisplayName("并发充足时，预算由配额决定，不应无限放大")
    void budgetIsBoundedWhenConcurrencyIsAmple() {
        // 并发拉到 60，能力远超配额；此时预算应贴着配额估时，而不是继续膨胀
        long ample = ElongCPSQueryPriceServiceImpl.roundBudgetSeconds(900, 4.0, 60);
        long exact = ElongCPSQueryPriceServiceImpl.roundBudgetSeconds(900, 4.0, 13);

        assertTrue(ample <= exact,
                "并发从刚够(13)加到富余(60)，预算不应变大——上限该由配额封顶");
        // 900 行 / 4 QPS = 225s，乘 1.5 加 60 = 397s；给个宽松上界防止公式失控
        assertTrue(ample < 900, "预算 " + ample + "s 过大：并发充足时应贴着配额估时");
    }

    @Test
    @DisplayName("兜底并发 1（安全侧）时预算要跟着放大，否则一上线就自我截断")
    void safeSideConcurrencyGetsEnoughBudget() {
        // §3.3.3 的安全侧兜底是并发 1。若 Nacos 读不到，900 行串行按 3.2s/行 需约 2880s，
        // 预算必须容得下，否则"退回安全行为"反而变成每轮截断
        long budget = ElongCPSQueryPriceServiceImpl.roundBudgetSeconds(900, 4.0, 1);
        assertTrue(budget > 2880,
                "预算 " + budget + "s 容不下串行跑完 900 行。兜底退回串行本是安全侧，"
                        + "预算不跟着放大就会让安全侧变成数据损失");
    }
}
