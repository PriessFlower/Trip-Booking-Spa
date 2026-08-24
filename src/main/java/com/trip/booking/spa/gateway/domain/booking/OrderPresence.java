package com.trip.booking.spa.gateway.domain.booking;

/**
 * 查单结果
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
