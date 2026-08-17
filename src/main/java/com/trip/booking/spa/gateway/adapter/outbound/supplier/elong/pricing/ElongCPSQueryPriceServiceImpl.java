package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.google.common.util.concurrent.RateLimiter;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 艺龙刷价：消费 {@code elong_query_price_task}，逐店查价并写入价格缓存。
 *
 * <p>结构照搬 {@code ExpediaCPSQueryPriceServiceImpl}（一轮一返回、Redisson 锁互斥、
 * 单行 try 隔离坏数据），差异全部来自艺龙的两条硬约束：
 *
 * <ul>
 *   <li><b>额度与 tg-trip-cursor 共享</b>：艺龙 10 QPS 是账号级硬额度，cursor 的刷价
 *       同时在用。2026-08-17 生产实测：0.62 QPS 时查价成功率 73%，降到约 0.4 QPS 后
 *       92%，失败几乎全是 A201010001（访问太频繁）。故默认 QPS 取 0.3 的保守值，
 *       且失败分类计数——频控与真实业务错误必须分开看，否则调速没有依据</li>
 *   <li><b>逐店查询</b>：一行任务 = 一次 hotel.detail 调用，不批量聚合（移植风险⑤）</li>
 * </ul>
 *
 * <p>本类不决定"何时刷"——cron 在 {@code ElongCPSQueryPriceTask}，默认只在 cursor 的
 * 刷价低谷时段执行（其刷价按小时极不均匀是有意设计的错峰，低谷 0.03 QPS、
 * 高峰 4.0 QPS，实测于生产库 hotel_price_freshness）。
 */
@Slf4j
@Service
public class ElongCPSQueryPriceServiceImpl implements ElongCPSQueryPriceService {

    /** 与定时调度、手动触发共用一把锁：两个入口互斥，避免并发消费同一批任务重复烧配额（§3.8.2） */
    private static final String LOCK_KEY = "lock:elong:cps:query-price";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ElongQueryPriceTaskMapper elongQueryPriceTaskMapper;

    @Resource
    private ElongPriceService elongPriceService;

    @Resource
    private Environment environment;

    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            log.info("[gate] {} 已被其他执行者持有，本次跳过, trigger={}", LOCK_KEY, trigger);
            return false;
        }
        try {
            return runOneRound(priority, temporaryUpgrade, trigger);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Boolean runOneRound(int priority, int temporaryUpgrade, String trigger) {
        long roundStart = System.currentTimeMillis();
        // 兜底 0.3 QPS 为安全侧（§3.3.3）：额度与 cursor 共享，宁可刷得慢也不要互相挤兑到双输
        double qps = environment.getProperty("task.elong-cps.qps", Double.class, 0.3);
        int batchSize = environment.getProperty("task.elong-cps.batch-size", Integer.class, 100);
        RateLimiter rateLimiter = RateLimiter.create(qps);
        List<ElongQueryPriceTask> list = elongQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("elongQueryPriceTask 本轮开始, trigger={}, priority={}, 取到 {} 行, batchSize={}, qps={}",
                trigger, priority, list.size(), batchSize, qps);
        if (CollectionUtils.isEmpty(list)) {
            log.info("elongQueryPriceTask 本轮结束, trigger={}, 无待刷任务, 耗时 {} ms",
                    trigger, System.currentTimeMillis() - roundStart);
            return true;
        }

        int onSale = 0;
        int empty = 0;
        int failed = 0;
        for (ElongQueryPriceTask task : list) {
            try {
                Date upgradeDeadline = task.getUpgradeDeadline();
                if (null == upgradeDeadline || !upgradeDeadline.after(new Date())) {
                    task.setTemporaryUpgrade(0);
                }
                task.setUpdateTime(new Date());
                elongQueryPriceTaskMapper.updateAddCount(task);

                LocalDate checkIn = LocalDate.now().plusDays(task.getDelayCheckIn());
                LocalDate checkOut = LocalDate.now().plusDays(task.getDelayCheckOut());
                rateLimiter.acquire();
                Monitor.recordOne("elong_cps_query_price_qps_" + priority);

                PriceReq request = PriceReq.builder().adultNum(1).childNum(0).guestType(0)
                        .childAges(new ArrayList<>()).checkIn(checkIn.toString())
                        .checkout(checkOut.toString()).roomNum(1).build();
                Supplier supplier = Supplier.builder()
                        .supplierId(SupplierSourceEnum.ELONG.getCode())
                        .sHotelId(task.getShId()).build();

                List<ProductRespDTO> products = elongPriceService.queryPricesCache(request, supplier);
                if (products == null) {
                    // 调用未取得结果：绝大多数是账号级频控（与 cursor 抢额度），
                    // 少数是网络/解析。两者都不该动缓存，计入 failed 供调速参考
                    failed++;
                } else if (products.isEmpty()) {
                    empty++;
                } else {
                    onSale++;
                }
            } catch (Exception e) {
                failed++;
                log.error("elongQueryPriceTask 单行异常, shId={}", task.getShId(), e);
            }
        }
        // §6.1.2：三态各自计数——"刷不出价"是频控还是真无房，处置完全不同
        log.info("elongQueryPriceTask 本轮结束, trigger={}, priority={}, 共 {} 行, 有在售={}, 无在售={}, 失败={}, 耗时 {} ms",
                trigger, priority, list.size(), onSale, empty, failed, System.currentTimeMillis() - roundStart);
        return true;
    }
}
