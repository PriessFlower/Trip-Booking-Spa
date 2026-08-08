package com.trip.booking.spa.core.task.ratehawk;

import com.google.common.util.concurrent.RateLimiter;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ratehawk价格缓存定时任务：消费 ratehawk_query_price_task 任务表，批量查价写入 Redis.
 *
 * @author dick_w
 */
@Slf4j
@Component
public class RateHawkCPSQueryPriceTask {

    @Autowired
    private RateHawkCPSQueryPriceService rateHawkCPSQueryPriceService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    /**
     * 闸口 task.ratehawk-cps.enabled：是否按 cron 自动刷价。
     * 误开风险=持续消耗 RateHawk 查价配额；误关风险=价格缓存停止更新，对外报价逐渐陈旧。
     * 执行面：全部节点（多实例由 Redisson 锁选出唯一执行者）。BackDoor 手动入口不受本闸约束。
     */
    @Scheduled(cron = "${task.ratehawk-cps.cron:0 15/30 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.ratehawk-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.ratehawk-cps.enabled=false，跳过本次调度");
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:ratehawkCpsQueryPrice");
        if (!lock.tryLock()) {
            log.info("[gate] task:lock:ratehawkCpsQueryPrice 已被其他实例持有，本实例跳过本次调度");
            return;
        }
        try {
            log.info("RateHawkCPSQueryPriceTask start");
            // qps限流 生产环境2.5  测试环境约0.16（1分钟10次）
            Double cacheQps = environment.getProperty("task.ratehawk-cps.qps", Double.class, 0.5);
            RateLimiter rateLimiter = RateLimiter.create(cacheQps);
            rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0, rateLimiter);
        } catch (Exception e) {
            log.error("RateHawkCPSQueryPriceTask ,error:", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
