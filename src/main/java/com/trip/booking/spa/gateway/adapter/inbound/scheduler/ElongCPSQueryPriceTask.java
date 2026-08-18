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
     *   <li><b>误开的后果</b>：<b>若 tg-trip-cursor 的艺龙刷价尚未停用，两边会抢同一份
     *       10 QPS 账号额度而双输</b>——2026-08-17 生产实测，速率从 0.4 升到 0.62 QPS，
     *       查价成功率就从 92% 掉到 73%，掉的部分全是 A201010001（访问太频繁）。
     *       <b>故开启本闸的前置是 cursor 侧已停刷艺龙</b>，二者不可同时全速运行</li>
     *   <li><b>误关的后果</b>：艺龙价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）。
     *       BackDoor 手动触发不受本闸约束，但共用同一把锁</li>
     * </ul>
     *
     * <p><b>cron 覆盖全时段</b>：{@code 0 0/10 * * * ?} 每 10 分钟一轮。按 cursor 停刷后
     * 额度独享设计——其艺龙刷价现状为 24h 内 45,235 条 / 16,998 家（平均约 0.52 QPS），
     * 而艺龙库共 23,584 家；本任务以 1 QPS × 每轮 500 家的节奏，每天可刷约 7.2 万条，
     * 即全库每天覆盖三轮，优于 cursor 现状。速率与批量见
     * {@code task.elong-cps.qps} / {@code batch-size}。
     */
    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    @Scheduled(cron = "${task.elong-cps.cron:0 0/10 * * * ?}")
    public void run() {
        // 闸口检查留在调度线程（毫秒级）；任务体派发到 elong 专属线程（F-2.7），
        // 调度线程立即释放，不再与其他供应商的任务互相排队
        if (!environment.getProperty("task.elong-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.elong-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("elong", "cps-query-price",
                () -> elongCPSQueryPriceService.queryPriceQueueTask(0, 0, "scheduled"));
    }
}
