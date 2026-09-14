package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing.DidaCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 道旅价格缓存定时任务：消费 {@code dida_query_price_task}，逐店查价写入 Redis。
 */
@Slf4j
@Component
public class DidaCPSQueryPriceTask {

    @Resource
    private DidaCPSQueryPriceService didaCPSQueryPriceService;

    @Resource
    private Environment environment;

    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    /**
     * 闸口 {@code task.dida-cps.enabled}（PROJECT.md §3.8.5 三项声明）：
     *
     * <ul>
     *   <li><b>误开的后果</b>：与 tg-trip-cursor 侧仍在跑的道旅刷价<b>叠加打同一个账号</b>
     *       （ClientID 相同），撞道旅的 QPS 限制（错误码 2022）。道旅未公开配额，故放量前
     *       必须看 {@code supplier_io_access{supplier="DIDA",status="throttled"}}</li>
     *   <li><b>误关的后果</b>：道旅价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）</li>
     * </ul>
     *
     * <p>cron 是看门狗不是节拍器（同另两家）：刷价服务自己连续消费到关闸或没活，
     * 正常情况下每次触发都会被供应商专属执行器拒掉（忙则跳过），那是预期行为。
     */
    @Scheduled(cron = "${task.dida-cps.cron:0 8/10 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.dida-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.dida-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("dida", "cps-query-price",
                () -> didaCPSQueryPriceService.queryPriceQueueTask("scheduled"));
    }
}
