package com.trip.booking.spa.core.task.expedia;

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

    /**
     * 闸口 task.expedia-cps.enabled：是否按 cron 自动刷价。
     * 误开风险=持续消耗 Expedia 查价配额；误关风险=价格缓存停止更新，走缓存的酒店对外报价逐渐陈旧。
     * 执行面：全部节点（多实例由 Redisson 锁选出唯一执行者）。BackDoor 手动入口不受本闸约束。
     */
    @Scheduled(cron = "${task.expedia-cps.cron:0 0/30 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.expedia-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.expedia-cps.enabled=false，跳过本次调度");
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:expediaCpsQueryPrice");
        if (!lock.tryLock()) {
            log.info("[gate] task:lock:expediaCpsQueryPrice 已被其他实例持有，本实例跳过本次调度");
            return;
        }
        try {
            log.info("ExpediaCPSQueryPriceTask start");
            Double cacheQps = environment.getProperty("task.expedia-cps.qps", Double.class, 0.5);
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
