package com.trip.booking.spa.gateway.domain.booking;

/**
 * 下单结果
 */
public enum BookingOutcome {

    /** 供应商已确认成单，供应商订单号必然有值 */
    SUCCESS,

    /**
     * 确定失败，重试必再失败。仅用于供应商明确给出业务性拒绝的场景
     * （满房、售罄、参数非法、额度不足等）。上游可据此直接终结订单并退款。
     */
    FAILED,

    /**
     * 结果不确定：请求可能已在供应商侧生效。
     * 上游<b>禁止</b>据此退款或终结订单，必须先查单确证。
     */
    UNKNOWN
}
