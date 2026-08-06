package com.trip.booking.spa.core.task;

import com.google.common.util.concurrent.RateLimiter;
import com.trip.booking.spa.core.api.expedia.service.ExpediaCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * expedia价格缓存定时任务：消费 expedia_query_price_task 任务表，批量查价写入 Redis.
 *
 * @author dick_w
 */
@Slf4j
@Component
public class ExpediaCPSQueryPriceTask {

    @Autowired
    private ExpediaCPSQueryPriceService expediaCPSQueryPriceService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Scheduled(cron = "${task.expedia-cps.cron:0 0/30 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.expedia-cps.enabled", Boolean.class, false)) {
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:expediaCpsQueryPrice");
        if (!lock.tryLock()) {
            log.info("ExpediaCPSQueryPriceTask 未抢到锁，本实例跳过");
            return;
        }
        try {
            log.info("ExpediaCPSQueryPriceTask start");
            Double cacheQps = environment.getProperty("expedia.query.price.cache.qps", Double.class, 0.5);
            RateLimiter rateLimiter = RateLimiter.create(cacheQps);
            expediaCPSQueryPriceService.queryPriceQueueTask(0, 0, rateLimiter);
        } catch (Exception e) {
            log.error("ExpediaCPSQueryPriceTask ,error:", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
