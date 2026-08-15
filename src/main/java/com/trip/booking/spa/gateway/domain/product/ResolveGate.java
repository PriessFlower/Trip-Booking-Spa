package com.trip.booking.spa.gateway.domain.product;

import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * resolve 换票的选票与容差门（docs/product-identity.md R-3.3），供应商无关。
 *
 * <p>输入是<b>已过硬门</b>的等价报价（R-3.2 的四门由 productKey 相等保证：键本身
 * 由房型ID+餐食+退改类+占用派生，键同即门过）。本类只做两件事：
 *
 * <ol>
 *   <li><b>选最低</b>：一个卖法下多张在售票是常态（艺龙同卖法 ~14 码轮换），选最便宜的</li>
 *   <li><b>容差门</b>：新价 ≤ 展示价 ×(1+容差) 才放行。超出即拒绝自动换票——宁可
 *       RATE_DEAD 让上游重新查价报价，也不静默按更高价格成交</li>
 * </ol>
 *
 * <p>展示价缺席时一律拒绝：没有基准就没有容差，自动换票的资损风险无从约束。
 * 这也是把 {@code totalPrice} 设为 resolve 前置条件的原因。
 *
 * <p>全部供应商共用本实现（R-3.6）——cursor 的反面是 5 条钩子位置各异的救回补丁，
 * 每条自带一套选票逻辑，其中一条只按最便宜救、不看餐食，产出了含早错配（订单 49046202）。
 */
public final class ResolveGate {

    private ResolveGate() {
    }

    /**
     * @param equivalents    已按 productKey 匹配过的等价报价
     * @param priceCents     报价 → 上游口径总价（分），必须与查价响应给上游的口径一致
     * @param seenPriceCents 客人所见展示价（分）；null 或非正值 → 拒绝
     * @param toleranceRatio 容差比例，如 0.02 = 2%
     * @return 最低价且过容差门的报价；无可用报价时 empty，调用方应回报 RATE_DEAD
     */
    public static <T> Optional<T> pickCheapestWithinTolerance(List<T> equivalents, ToIntFunction<T> priceCents,
                                                              Integer seenPriceCents, double toleranceRatio) {
        if (equivalents == null || equivalents.isEmpty()
                || seenPriceCents == null || seenPriceCents <= 0) {
            return Optional.empty();
        }
        T cheapest = equivalents.get(0);
        for (T candidate : equivalents) {
            if (priceCents.applyAsInt(candidate) < priceCents.applyAsInt(cheapest)) {
                cheapest = candidate;
            }
        }
        if (priceCents.applyAsInt(cheapest) > seenPriceCents * (1 + toleranceRatio)) {
            return Optional.empty();
        }
        return Optional.of(cheapest);
    }
}
