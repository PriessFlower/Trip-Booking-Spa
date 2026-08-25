package com.trip.booking.spa.gateway.domain.booking;

/**
 * 验价结果
 */
public enum CheckPriceOutcome {


    /** 有货，但未向供应商确认可订性 */
    AVAILABLE,

    /** 验价通过，可下单。此时报价句柄与价格字段必然有值 */
    BOOKABLE,

    /** 供应商明确回答该产品已售罄。确定性结果 */
    SOLD_OUT,

    /** 所点的报价已不存在于供应商当前的报价中——报价标识过期、产品下架、或所选床型已不可选。*/
    RATE_DEAD,

    /** 未能得出结论：调用超时、被限流、供应商返回 5xx，或响应无法判读。*/
    INDETERMINATE
}
