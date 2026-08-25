package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

/**
 * 艺龙刷价：消费查价任务队列，把价格预热进缓存。
 */
public interface ElongCPSQueryPriceService {

    /**
     * 连续消费待刷任务，直到关闸或各档都没活。档位序列由实现决定，调用方不再逐档触发。
     *
     * <p>不是"跑一轮就返回"——cron 每 10 分钟才给一次活而一轮约 5 分钟，会空等掉 45% 的时间。
     * 关闸仍然生效：每轮之间重新读闸（AbstractCPSQueryPriceService 的循环）。
     *
     * @param trigger 触发来源（scheduled / backdoor），仅用于日志溯源
     * @return 是否真正执行（false=锁被他人持有，本次跳过）
     */
    Boolean queryPriceQueueTask(String trigger);
}
