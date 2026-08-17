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
     *   <li><b>误开的后果</b>：与 tg-trip-cursor 的艺龙刷价<b>抢同一份 10 QPS 账号额度</b>。
     *       两边同时全速刷会双输——2026-08-17 生产实测，速率从 0.4 升到 0.62 QPS，
     *       查价成功率就从 92% 掉到 73%，掉的部分全是 A201010001（访问太频繁）。
     *       故本闸默认关，且开启前须确认 cron 落在 cursor 的刷价低谷时段</li>
     *   <li><b>误关的后果</b>：艺龙价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）。
     *       BackDoor 手动触发不受本闸约束，但共用同一把锁</li>
     * </ul>
     *
     * <p><b>cron 默认值即错峰</b>：{@code 0 0/10 0-7 * * ?} 只在 UTC 00:00–07:59 每 10 分钟跑一轮。
     * 该窗口取自生产库 hotel_price_freshness 的实测分布——cursor 在此时段的刷价量为
     * 86~414 条/小时（约 0.03~0.11 QPS），额度几乎闲置；而其高峰在 UTC 15:00–16:00
     * （最高 14,575 条/小时 ≈ 4 QPS）。错峰是 cursor 侧的有意设计，本任务纳入同一套节奏，
     * 而不是去和它抢。cursor 停刷艺龙后，此 cron 可放开到全时段。
     */
    @Scheduled(cron = "${task.elong-cps.cron:0 0/10 0-7 * * ?}")
    public void run() {
        if (!environment.getProperty("task.elong-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.elong-cps.enabled=false，跳过本次调度");
            return;
        }
        try {
            elongCPSQueryPriceService.queryPriceQueueTask(0, 0, "scheduled");
        } catch (Exception e) {
            log.error("ElongCPSQueryPriceTask error:", e);
        }
    }
}
