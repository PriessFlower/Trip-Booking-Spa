package com.bingo.hotel.spa.intl.core.api.ratehawk.service;

import com.google.common.util.concurrent.RateLimiter;

/**
 * @description:rateHawk查价缓存接口
 * @author: dick_w
 * @date: 2025/3/10 17:17
 * @param:
 * @return:
 **/
public interface RateHawkCPSQueryPriceService {

    Boolean queryPriceQueueTask(int priority, int temporaryUpgrade,RateLimiter rateLimiter);

}
