package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

/**
 * 飞猪刷价：消费查价任务队列，把价格预热进缓存。语义同艺龙同名接口
 * （连续消费到关闸或没活，返回 false=锁被他人持有本次跳过）。
 */
public interface FliggyCPSQueryPriceService {

    Boolean queryPriceQueueTask(String trigger);
}
