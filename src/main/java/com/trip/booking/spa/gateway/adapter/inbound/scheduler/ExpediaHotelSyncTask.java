package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaCatalogSeedService;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expedia 酒店静态数据增量同步.
 */
@Slf4j
@Component
public class ExpediaHotelSyncTask {

    @Autowired
    private ExpediaCatalogSeedService catalogSeedService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Scheduled(cron = "${task.expedia-hotel-sync.cron:0 0 2 * * ?}")
    public void run() {
        if (!environment.getProperty("task.expedia-hotel-sync.enabled", Boolean.class, false)) {
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:expediaHotelSync");
        if (!lock.tryLock()) {
            log.info("ExpediaHotelSyncTask 未抢到锁，本实例跳过");
            return;
        }
        try {
            log.info("ExpediaHotelSyncTask is start!");
            int updateDays = environment.getProperty("task.expedia-hotel-sync.update-days", Integer.class, 1);
            // 新链路：catalog 增量清单（最近 updateDays 天）→ 双语摄取 → 加工进目录（承接旧 saveOrUpdateHotelInfo 语义）
            Object result = catalogSeedService.seed(Set.of(), 0, true, true, true, updateDays, 0);
            log.info("ExpediaHotelSyncTask seed result: {}", result);
            log.info("ExpediaHotelSyncTask is end!");
        } catch (Exception e) {
            log.error("ExpediaHotelSyncTask ,error:", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
