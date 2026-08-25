package com.trip.booking.spa.gateway.domain.booking;

/**
 * 取消结果
 */
public enum CancelOutcome {

    /** 供应商已确认取消，该订单的全部房间均处于已取消状态 */
    SUCCESS,

    /**
     * 确定失败，重试必再失败。仅用于供应商明确给出业务性拒绝的场景
     * （订单不存在、已过免费取消期限、订单状态不允许取消等）。
     */
    FAILED,

    /**
     * 结果不确定：取消请求可能已在供应商侧生效。
     * 上游<b>禁止</b>据此判定订单仍然有效，必须先查单确证。
     */
    UNKNOWN
}
