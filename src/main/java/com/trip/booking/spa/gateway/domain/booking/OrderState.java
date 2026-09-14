package com.trip.booking.spa.gateway.domain.booking;

/**
 * 订单在供应商侧的状态，②③层的语义取值。
 *
 * <p><b>对外那张数字码表（10/20/21/22/30/31/32）不在这里</b>，在 ① 的
 * {@code OrderQueryMapping}——此前艺龙与 Expedia 各自定义了一套 {@code ORDER_STATUS_*}
 * 常量并各写一个 {@code mapOrderStatus}，同一张码表两种笔迹，加第三家就是第三份。
 *
 * <p><b>识别不出就不要取值</b>：本枚举没有 UNKNOWN 项，映射不出的一律留 {@code null}，
 * 由 {@code supplierOrderStatus} 保留供应商原文。给未知状态安一个默认值，等于把
 * "不知道"说成"知道"，上游据此做的每一步都是错的。
 */
public enum OrderState {

    /** 我方已建单，尚未提交供应商 */
    CREATED,
    /** 已提交供应商，结果未定 */
    BOOKING,
    /** 供应商确认预订成立 */
    BOOKED,
    /** 供应商确认预订未成立（如满房打回） */
    BOOK_FAILED,
    /** 已发起取消，结果未定 */
    CANCELING,
    /** 供应商确认已取消 */
    CANCELED,
    /** 取消被供应商拒绝 */
    CANCEL_FAILED
}
