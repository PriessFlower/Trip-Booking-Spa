package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
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
    private Environment environment;

    /**
     * 闸口 task.expedia-cps.enabled：是否按 cron 自动刷价。
     * 误开风险=持续消耗 Expedia 查价配额；误关风险=价格缓存停止更新，走缓存的酒店对外报价逐渐陈旧。
     * 执行面：全部节点（多实例由 Redisson 锁选出唯一执行者）。BackDoor 手动入口不受本闸约束。
     */
    @Autowired
    private SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.expedia-cps.cron:0 0/30 * * * ?}")
    public void run() {
        // 闸口检查留在调度线程；任务体进 expedia 专属线程（F-2.7），不再挡住别家的调度
        if (!environment.getProperty("task.expedia-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.expedia-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("expedia", "cps-query-price",
                () -> expediaCPSQueryPriceService.queryPriceQueueTask(0, 0, "scheduled"));
    }
}
