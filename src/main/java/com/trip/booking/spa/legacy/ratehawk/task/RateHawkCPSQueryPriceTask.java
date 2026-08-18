package com.trip.booking.spa.legacy.ratehawk.task;

import com.trip.booking.spa.legacy.ratehawk.service.RateHawkCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
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
    private Environment environment;

    /**
     * 闸口 task.ratehawk-cps.enabled：是否按 cron 自动刷价。
     * 误开风险=持续消耗 RateHawk 查价配额；误关风险=价格缓存停止更新，对外报价逐渐陈旧。
     * 执行面：全部节点（多实例由 Redisson 锁选出唯一执行者）。BackDoor 手动入口不受本闸约束。
     */
    @Autowired
    private com.trip.booking.spa.gateway.adapter.inbound.scheduler.SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.ratehawk-cps.cron:0 0/30 * * * ?}")
    public void run() {
        // 闸口检查留在调度线程；任务体进 ratehawk 专属线程（F-2.7）。legacy→gateway
        // 方向的依赖是允许的（arch 规则只禁新结构依赖 legacy）
        if (!environment.getProperty("task.ratehawk-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.ratehawk-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("ratehawk", "cps-query-price",
                () -> rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0, "scheduled"));
    }
}
