package com.trip.booking.spa.core.api.common.enums;

/**
 * 查单结果三态。
 *
 * <p>本枚举与 {@link BookingOutcome} 配对使用：下单回报 {@link BookingOutcome#UNKNOWN} 时，
 * 上游唯一的确证手段就是查单，而<b>查单本身也会失败</b>。若不把「确实没有这笔订单」与
 * 「没查出来」区分开，上游就只能猜——猜错哪一边都是资损：
 *
 * <ul>
 *   <li>把 {@link #INDETERMINATE} 当成 {@link #NOT_FOUND} → 重新下单，可能重复占房重复付款</li>
 *   <li>把 {@link #NOT_FOUND} 当成 {@link #INDETERMINATE} → 订单永久悬空，旅客既没房也没退款</li>
 * </ul>
 *
 * <p>所以这三态不可合并，也不可用「返回 null」表达其中任何一个。
 *
 * <p><b>判定纪律</b>：只有供应商明确回答「没有这笔订单」才可判 {@link #NOT_FOUND}；
 * 超时、限流、5xx、响应无法判读一律判 {@link #INDETERMINATE}。与 {@link BookingOutcome}
 * 一样，判据向「不确定」倾斜是有意为之。
 */
public enum OrderPresence {

    /** 确证订单存在。订单信息字段有值 */
    FOUND,

    /**
     * 确证订单不存在。<b>仅此态允许上游重新下单</b>。
     */
    NOT_FOUND,

    /**
     * 查单未能得出结论。上游<b>禁止</b>据此重新下单，也禁止据此退款，应稍后重试查单。
     */
    INDETERMINATE
}
