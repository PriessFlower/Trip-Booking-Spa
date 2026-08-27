package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.FliggyCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 飞猪价格缓存定时任务：消费 {@code fliggy_query_price_task}，逐店查价写入 Redis。
 *
 * <p>闸口 {@code task.fliggy-cps.enabled}（§3.8.5 三项声明）：
 * <ul>
 *   <li><b>误开的后果</b>：与 cursor 侧飞猪 keeper（常驻 ~8.5 QPS）共用同一个 appKey 的
 *       平台流控池——叠加超限表现为平台 code 7，看
 *       {@code supplier_io_access{supplier=FLIGGY,status=throttled}}</li>
 *   <li><b>误关的后果</b>：飞猪缓存价陈旧。不资损——下单前必经验价（现取现验）</li>
 *   <li><b>生效执行面</b>：全部节点（Redisson 锁选出唯一执行者）；BackDoor 不受本闸约束但共锁</li>
 * </ul>
 *
 * <p>cron 是看门狗不是节拍器：服务自己连续消费到关闸或没活（同艺龙）。
 */
@Slf4j
@Component
public class FliggyCPSQueryPriceTask {

    @Resource
    private FliggyCPSQueryPriceService fliggyCPSQueryPriceService;

    @Resource
    private Environment environment;

    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.fliggy-cps.cron:0 5/10 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.fliggy-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.fliggy-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("fliggy", "cps-query-price",
                () -> fliggyCPSQueryPriceService.queryPriceQueueTask("scheduled"));
    }
}
