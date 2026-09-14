package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing.ClwyCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 差旅无忧价格缓存定时任务：消费 {@code clwy_query_price_task}，逐店查价写入 Redis。
 */
@Slf4j
@Component
public class ClwyCPSQueryPriceTask {

    @Resource
    private ClwyCPSQueryPriceService clwyCPSQueryPriceService;

    @Resource
    private Environment environment;

    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    /**
     * 闸口 {@code task.clwy-cps.enabled}（PROJECT.md §3.8.5 三项声明）：
     *
     * <ul>
     *   <li><b>误开的后果</b>：与 tg-trip-cursor 侧仍在跑的差旅无忧刷价<b>叠加打同一个账号</b>
     *       （wid 相同），撞对方未公开的额度。本家没有专门的频控错误码——失败只有一个
     *       {@code code=500} 加一句文案，超额长什么样我们没见过，故放量前必须看
     *       {@code supplier_io_access{supplier="CLWY"}} 的失败率，不能等错误码报警</li>
     *   <li><b>误关的后果</b>：价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）</li>
     * </ul>
     *
     * <p>cron 是看门狗不是节拍器（同另四家）：刷价服务自己连续消费到关闸或没活，
     * 正常情况下每次触发都会被供应商专属执行器拒掉（忙则跳过），那是预期行为。
     */
    @Scheduled(cron = "${task.clwy-cps.cron:0 3/10 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.clwy-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.clwy-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("clwy", "cps-query-price",
                () -> clwyCPSQueryPriceService.queryPriceQueueTask("scheduled"));
    }
}
