package com.trip.booking.spa.gateway.application.checkprice;

/**
 * resolve 候选：已过硬门（productKey 与请求相等，R-3.2）的一条现货报价，
 * 及其<b>上游口径</b>总价（分）——必须与查价响应透给上游的 totalPrice 同一算法，
 * 否则容差门量的不是同一把尺子。
 */
public record ResolveCandidate<C>(C candidate, int priceCents) {
}
