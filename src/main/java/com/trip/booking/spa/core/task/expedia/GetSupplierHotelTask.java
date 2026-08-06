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
 * 获取供应商酒店任务.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/15
 * @since : 1.0
 **/
@Slf4j
@Component
public class GetSupplierHotelTask {

    @Autowired
    private ExpediaStaticInfoService expediaStaticInfoService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Scheduled(cron = "${task.supplier-hotel-sync.cron:0 0 2 * * ?}")
    public void run() {
        if (!environment.getProperty("task.supplier-hotel-sync.enabled", Boolean.class, false)) {
            return;
        }
        RLock lock = redissonClient.getLock("task:lock:supplierHotelSync");
        if (!lock.tryLock()) {
            log.info("GetSupplierHotelTask 未抢到锁，本实例跳过");
            return;
        }
        try {
            log.info("GetSupplierHotelTask is start!");
            Integer supplierId = environment.getProperty("task.supplier-hotel-sync.supplier-id", Integer.class, 10005);
            int updateDays = environment.getProperty("task.supplier-hotel-sync.update-days", Integer.class, 1);
            switch (supplierId) {
                case 10005:
                    expediaStaticInfoService.saveOrUpdateHotelInfo(true, false, updateDays, null, 0);
                    break;
                default:
                    log.info("GetSupplierHotelTask ,supplierId:{} no method", supplierId);
            }
            log.info("GetSupplierHotelTask is end!");
        } catch (Exception e) {
            log.error("GetSupplierHotelTask ,error:", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
