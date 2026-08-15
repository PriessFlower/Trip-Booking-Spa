package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;


/**
 * @description:expedia查价缓存service
 * @author: dick_w
 * @date: 2025/3/17 14:23
 * @param:
 * @return:
 **/
public interface ExpediaCPSQueryPriceService {

    /**
     * 消费一轮刷价任务。速率、批量与互斥锁均在实现内解析，调用方只需说明触发来源。
     *
     * @param trigger 触发来源，写入日志用于区分定时调度与人工触发，取值 scheduled / manual
     */
    Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger);

}
