package com.trip.booking.spa.core.api.common.identity;

/**
 * productKey 的退改成分：粗分类（docs/product-identity.md R-5.1）。
 *
 * <p>完整退改条款（分段、截止时间、罚金结构）是<b>属性</b>，跟着报价走、存进订单快照；
 * 键里只放分类——因为条款里的截止时间是绝对时间戳，随查询日期漂移，进键则键不稳。
 *
 * <p>UNKNOWN 是合法取值：解析不出退改时禁止兜底成任何确定值（R-5.4，反面教材见
 * cursor：dida 餐食未知兜成 0、clwy 退改未知兜成不可退）。带 UNKNOWN 的键可以在
 * 实时链路上流转，但不得写入目录（不参与等价类匹配）。
 */
public enum CancelClass {

    /** 存在免费取消窗口（不论窗口长短、之后罚金如何） */
    FREE_CANCELLABLE,

    /** 全程不可退 */
    NON_REFUNDABLE,

    /** 解析不出。不许说成前两者之一 */
    UNKNOWN
}
