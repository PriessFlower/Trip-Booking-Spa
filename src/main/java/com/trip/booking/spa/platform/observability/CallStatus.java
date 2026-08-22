package com.trip.booking.spa.platform.observability;

import java.util.Locale;

/**
 * 一次供应商调用的结果
 */
public enum CallStatus {

    /** 供应商答了，且有可卖报价 */
    QUOTED,

    /** 供应商答了，但无房/无价。业务正常态，不是故障 */
    NO_INVENTORY,

    /** 供应商答了，但返业务错误码（参数、权限、报价码过期） */
    REJECTED,

    /** 被限流：本地闸门拦下，或供应商返频控码 */
    THROTTLED,

    /** 超时无响应 */
    TIMEOUT,

    /** 其余异常：连接失败、解析失败 */
    ERROR;

    /** 标签值一律小写，与 Prometheus 惯例一致 */
    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
