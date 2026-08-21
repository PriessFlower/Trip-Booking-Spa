package com.trip.booking.spa.platform.observability;

import java.util.Locale;

/**
 * 一次供应商调用的终态。互斥且穷尽——{@code sum by (status)} 必须等于调用总数
 * （docs/observability.md O-3.3）。
 *
 * <p>取值都带宾语，看图时不必翻代码就知道说的是什么（O-3.1）。此前用的是
 * {@code ok} / {@code all} / {@code success}，看板上无法判断「什么 ok」；而
 * {@code ok} 实际含义还是「全部调用」而非「非空调用」——空结果被记两遍，据此算出的
 * 空结果占比系统性偏低。
 *
 * <p>本枚举是 {@code status} 标签值的唯一出处（O-2.4）。校验类结果不用这套词表，
 * 走 {@code outcome} 键（见 {@link MetricTags#OUTCOME}）。
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
