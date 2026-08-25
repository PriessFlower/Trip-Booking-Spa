package com.trip.booking.spa.gateway.domain.booking;

/**
 * 查价结果
 */
public enum PricingOutcome {

    /** 查到了可售产品。此时产品列表必然非空 */
    AVAILABLE,

    /**
     * 供应商明确回答该店该住期无可售产品。确定性结果，重试不会改变。
     */
    NO_INVENTORY,

    /**
     * 未能得出结论：调用超时、被限流、供应商返回 5xx、响应无法判读，或凭据未配置。
     */
    INDETERMINATE
}
