package com.trip.booking.spa.core.api.ratehawk.service;


/**
 * @description:rateHawk查价缓存接口
 * @author: dick_w
 * @date: 2025/3/10 17:17
 * @param:
 * @return:
 **/
public interface RateHawkCPSQueryPriceService {

    /**
     * 消费一轮刷价任务。速率、批量与互斥锁均在实现内解析，调用方只需说明触发来源。
     *
     * @param trigger 触发来源，写入日志用于区分定时调度与人工触发，取值 scheduled / manual
     */
    Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger);

}
