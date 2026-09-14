package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing.MeituanCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 美团价格缓存定时任务：消费 {@code meituan_query_price_task}，逐店查价写入 Redis。
 */
@Slf4j
@Component
public class MeituanCPSQueryPriceTask {

    @Resource
    private MeituanCPSQueryPriceService meituanCPSQueryPriceService;

    @Resource
    private Environment environment;

    @Resource
    private SupplierTaskExecutors supplierTaskExecutors;

    /**
     * 闸口 {@code task.meituan-cps.enabled}（PROJECT.md §3.8.5 三项声明）：
     *
     * <ul>
     *   <li><b>误开的后果</b>：与 tg-trip-cursor 侧仍在跑的美团刷价<b>叠加打同一个账号</b>
     *       （partnerId/accessKey 相同），撞美团的额度。官方只给了下单前校验的限流
     *       （1000 次/分钟），批量查价那页的"接口限流"是空的——即我们不知道上限在哪，
     *       故放量必须看 {@code supplier_io_access{supplier="MEITUAN"}} 的失败率抬升</li>
     *   <li><b>误关的后果</b>：价格缓存不更新，走缓存的报价逐渐陈旧。不资损——
     *       下单前必经验价，陈价最多让旅客看到的价与验后价不符而重新选</li>
     *   <li><b>生效执行面</b>：全部节点（多实例由 Redisson 锁选出唯一执行者）</li>
     * </ul>
     *
     * <p>cron 是看门狗不是节拍器（同另五家）：刷价服务自己连续消费到关闸或没活，
     * 正常情况下每次触发都会被供应商专属执行器拒掉（忙则跳过），那是预期行为。
     */
    @Scheduled(cron = "${task.meituan-cps.cron:0 7/10 * * * ?}")
    public void run() {
        if (!environment.getProperty("task.meituan-cps.enabled", Boolean.class, false)) {
            log.info("[gate] task.meituan-cps.enabled=false，跳过本次调度");
            return;
        }
        supplierTaskExecutors.submit("meituan", "cps-query-price",
                () -> meituanCPSQueryPriceService.queryPriceQueueTask("scheduled"));
    }
}
