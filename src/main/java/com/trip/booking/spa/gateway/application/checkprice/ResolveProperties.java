package com.trip.booking.spa.gateway.application.checkprice;

/**
 * resolve 换票的三个可调项（docs/product-identity.md R-3.3），每家在自己的
 * {@code supplier.<家>.resolve-*} 配置类上实现。模板只读不写。
 */
public interface ResolveProperties {

    /** 闸口 {@code supplier.<家>.resolve-enabled}：关闭时令牌死即 RATE_DEAD，不换票 */
    boolean isResolveEnabled();

    /** 容差比例：新价 ≤ 展示价 × (1 + 本值) 才许自动换票 */
    double getResolvePriceTolerance();

    /** 容差绝对帽（分）：单笔自动让利上限，与比例门取严 */
    int getResolvePriceCapCents();
}
