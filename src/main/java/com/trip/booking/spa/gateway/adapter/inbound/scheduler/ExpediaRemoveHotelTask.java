package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.catalog.ExpediaCatalogTransformService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaStaticDataIngestionService;

import java.util.List;
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
    private ExpediaStaticDataIngestionService ingestionService;

    @Autowired
    private ExpediaCatalogTransformService transformService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Autowired
    private SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.expedia-remove-hotel.cron:0 0 4 * * ?}")
    public void run() {
        if (!environment.getProperty("task.expedia-remove-hotel.enabled", Boolean.class, false)) {
            return;
        }
        // 抢锁→干活→放锁整体进 expedia 专属线程（F-2.7）——Redisson 锁绑定持有线程
        supplierTaskExecutors.submit("expedia", "remove-hotel", this::doRun);
    }

    private void doRun() {
        RLock lock = redissonClient.getLock("task:lock:expediaRemoveHotel");
        if (!lock.tryLock()) {
            log.info("ExpediaRemoveHotelTask 未抢到锁，本实例跳过");
            return;
        }
        try {
            log.info("ExpediaRemoveHotelTask is start!");
            String deleteDate = environment.getProperty("task.expedia-remove-hotel.delete-date", "");
            // Inactive API 拉下线名单 → 快照置 inactive → 目录置 del（承接旧 deleteHotelInfo 语义）
            List<String> inactiveIds = ingestionService.fetchAndMarkInactive(deleteDate);
            int deactivated = transformService.deactivateHotels(inactiveIds);
            log.info("ExpediaRemoveHotelTask 下线酒店 {} 家，目录置 del {} 行", inactiveIds.size(), deactivated);
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
