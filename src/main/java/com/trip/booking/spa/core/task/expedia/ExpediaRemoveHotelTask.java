package com.trip.booking.spa.core.task.expedia;

import com.trip.booking.spa.core.api.expedia.service.ExpediaStaticInfoService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理已下线的 Expedia 酒店.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/15
 * @since : 1.0
 **/
@Slf4j
@Component
public class ExpediaRemoveHotelTask {

    @Autowired
    private ExpediaStaticInfoService expediaStaticInfoService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Scheduled(cron = "${task.expedia-remove-hotel.cron:0 0 4 * * ?}")
    public void run() {
        if (!environment.getProperty("task.expedia-remove-hotel.enabled", Boolean.class, false)) {
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:expediaRemoveHotel");
        if (!lock.tryLock()) {
            log.info("ExpediaRemoveHotelTask 未抢到锁，本实例跳过");
            return;
        }
        try {
            log.info("ExpediaRemoveHotelTask is start!");
            String deleteDate = environment.getProperty("task.expedia-remove-hotel.delete-date", "");
            expediaStaticInfoService.deleteHotelInfo(deleteDate);
            log.info("ExpediaRemoveHotelTask is end!");
        } catch (Exception e) {
            log.error("ExpediaRemoveHotelTask ,error:", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
