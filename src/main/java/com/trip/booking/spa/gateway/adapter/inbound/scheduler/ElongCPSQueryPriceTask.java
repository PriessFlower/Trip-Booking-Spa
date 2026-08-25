package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.ElongCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 艺龙价格缓存定时任务：消费 {@code elong_query_price_task}，逐店查价写入 Redis。
 */
@Slf4j
@Component
public class ElongCPSQueryPriceTask {

    @Resource
    private ElongCPSQueryPriceService elongCPSQueryPriceService;

    @Resource
    private Environment environment;

    /**
     * 闸口 {@code task.elong-cps.enabled}（PROJECT.md §3.8.5 三项声明）：
     *
     * <ul>
     *   <li><b>误开的后果</b>：与其他吃 {@code hotel.detail} 的调用方抢同一个接口桶。历史教训：
     *       2026-08-17 与 tg-trip-cursor 同时刷价时，速率从 0.4 升到 0.62 QPS，查价成功率就从
     *       92% 掉到 73%，掉的全是 A201010001（访问太频繁）——两边叠加的瞬时峰值撞上了艺龙对
     *       {@code hotel.detail} 的每秒限制。cursor 侧已于 2026-08-19 实测确认对艺龙零调用</li>
     *   <li><b>误关的后果</b>：艺龙价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）。
     *       BackDoor 手动触发不受本闸约束，但共用同一把锁</li>
     * </ul>
     *
     * <p><b>cron 现在是看门狗，不是节拍器</b>（2026-08-25）：刷价服务自己连续消费到关闸或没活
     * （{@code AbstractCPSQueryPriceService} 的循环），所以正常情况下这里每次触发都会被供应商
     * 专属执行器拒掉（单线程 + SynchronousQueue，F-2.7 忙则跳过）——那是预期行为，不是故障。
     * 它存在的唯一理由是<b>自愈</b>：循环若因意外退出，下一个 cron 点把它重新拉起。
     *
     * <p>因此 cron 周期不再决定刷价节奏（那由循环和批量决定），只决定"挂了多久会被拉起来"。
     */
    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.elong-cps.cron:0 0/10 * * * ?}")
    public void run() {
        // 闸口检查留在调度线程（毫秒级）；任务体派发到 elong 专属线程（F-2.7），
        // 调度线程立即释放，不再与其他供应商的任务互相排队。
        // 循环内每轮还会再读一次闸——这里这次只是省掉一次无用派发
        if (!environment.getProperty("task.elong-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.elong-cps.enabled=false，跳过本次调度");
            return;
        }
        // 档位序列（成交→高频→常规→远期）在服务内声明，调度侧不再逐档触发：
        // 档序是刷价语义（哪档更急），属供应商适配层的知识，不该散在调度类里
        supplierTaskExecutors.submit("elong", "cps-query-price",
                () -> elongCPSQueryPriceService.queryPriceQueueTask("scheduled"));
    }
}
