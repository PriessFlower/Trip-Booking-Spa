package com.bingo.hotel.spa.intl.core.api.expedia.service;

import com.google.common.util.concurrent.RateLimiter;

/**
 * @description:expedia查价缓存service
 * @author: dick_w
 * @date: 2025/3/17 14:23
 * @param:
 * @return:
 **/
public interface ExpediaCPSQueryPriceService {

    Boolean queryPriceQueueTask(int priority, int temporaryUpgrade,RateLimiter rateLimiter);

}
