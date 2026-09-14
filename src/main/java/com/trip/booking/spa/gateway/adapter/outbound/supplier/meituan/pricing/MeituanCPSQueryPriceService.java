package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

/**
 * 美团刷价的对外触发口：定时任务与人工后门共用，两个入口由同一把 Redisson 锁互斥（§3.8.2）。
 */
public interface MeituanCPSQueryPriceService {

    /**
     * 连续消费到关闸或没活。
     *
     * @param trigger 触发来源，仅用于日志区分（scheduled / backdoor）
     * @return true=正常跑完（含"没活"）；false=没拿到锁
     */
    Boolean queryPriceQueueTask(String trigger);
}
