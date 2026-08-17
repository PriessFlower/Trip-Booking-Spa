package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

/**
 * 艺龙刷价:消费查价任务队列,把价格预热进缓存。
 */
public interface ElongCPSQueryPriceService {

    /**
     * 消费一轮待刷任务（取一批、逐行刷完即返回，不常驻循环）。
     *
     * @param priority         优先级分层
     * @param temporaryUpgrade 1=连同临时升级的一并取
     * @param trigger          触发来源（scheduled / backdoor），仅用于日志溯源
     * @return 是否真正执行了一轮（false=锁被他人持有，本次跳过）
     */
    Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger);
}
