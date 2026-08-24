package com.trip.booking.spa.gateway.domain.booking;

/**
 * 上游本次要多确定的答案。<b>只说要什么，不说供应商跑什么</b>——
 * 各家为此要调几个接口是适配层的事。
 */
public enum VerifyLevel {

    /** 只要「有没有货」。不要求可订证据，也不保证会拿到报价句柄 */
    AVAILABILITY,

    /** 要「此刻可下单」这个结论，以及能下单的句柄 */
    BOOKABLE
}
