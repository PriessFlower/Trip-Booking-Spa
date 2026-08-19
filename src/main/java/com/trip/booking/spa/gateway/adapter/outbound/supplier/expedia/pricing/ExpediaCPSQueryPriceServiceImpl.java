package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaCPSQueryPriceService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaPriceService;
import com.trip.booking.spa.platform.observability.Monitor;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Expedia 刷价缓存：消费 expedia_query_price_task 队列，把查回的价格写入我方 Redis 缓存。
 *
 * <p>定时入口见 {@code ExpediaCPSQueryPriceTask}，手动入口见 {@code BackDoorController}
 * 的 {@code /hotel/expedia/priceCache}，两者共用 {@link #LOCK_KEY} 互斥。</p>
 */
@Slf4j
@Service
public class ExpediaCPSQueryPriceServiceImpl implements ExpediaCPSQueryPriceService {

    /** 刷价互斥锁：定时调度与 BackDoor 手动触发共用，保证同一时刻仅一个执行者 */
    private static final String LOCK_KEY = "task:lock:expediaCpsQueryPrice";

    /**
     * 运维配置的读取入口。task.expedia-cps.{qps,batch-size} 权威取值由 Nacos 下发；
     * 代码默认值取安全侧（PROJECT.md §3.3.3）——qps 0.5、batch-size 200，
     * 缺配置时刷价变慢但不会突发大量请求。经 Environment 实时读取，改 Nacos 下一轮即生效。
     */
    @Autowired
    private Environment environment;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ExpediaQueryPriceTaskMapper expediaQueryPriceTaskMapper;

    @Autowired
    private ExpediaPriceService expediaPriceService;

    /**
     * 单次调用只消费一轮：取一批（SQL 按 update_time 升序，条数为 batch-size）、逐行刷完即返回。
     *
     * <p>不再无限循环。原实现的内层 while 永不退出——取任务 SQL 按 update_time 排序，而处理时会
     * 更新该字段，只要表里有行 list 就不会为空，于是循环长期占锁运行，唯一刹车是启动期绑定的
     * loop-enabled（改 Nacos 对运行中实例无效）。改为一轮一返回后：刷完由 cron 再次触发，
     * task.expedia-cps.enabled 关闸最迟在一个调度周期内真正停止做功（PROJECT.md §3.8.2、§3.8.3）。
     */
    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, String trigger) {
        // 锁在此处而非调用方：定时调度与 BackDoor 手动触发共用同一把锁，
        // 使两者互斥，避免并发消费同一批任务、重复消耗供应商配额（§3.8.2 一事一闸）
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
        // 速率同样在此处解析：两个入口取同一配置，不再各自写死（原 BackDoor 写死 1 QPS）
        double qps = environment.getProperty("task.expedia-cps.qps", Double.class, 0.5);
        RateLimiter rateLimiter = RateLimiter.create(qps);
        int batchSize = environment.getProperty("task.expedia-cps.batch-size", Integer.class, 200);
        List<ExpediaQueryPriceTask> list = expediaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("expediaQueryPriceTask 本轮开始, trigger={}, priority={}, 取到 {} 行, batchSize={}, qps={}",
                trigger, priority, list.size(), batchSize, qps);
        if (CollectionUtils.isEmpty(list)) {
            log.info("expediaQueryPriceTask 本轮结束, trigger={}, 无待刷任务, 耗时 {} ms",
                    trigger, System.currentTimeMillis() - roundStart);
            return true;
        }
        int succeeded = 0;
        int failed = 0;
        for (ExpediaQueryPriceTask expediaQueryPriceTask : list) {
            // 单行处理整体入 try：坏数据只跳过本行，不影响同批其余行
            try {
                //升级期限为空视为已过期
                Date upgradeDeadline = expediaQueryPriceTask.getUpgradeDeadline();
                if (null == upgradeDeadline || !upgradeDeadline.after(new Date())) {
                    expediaQueryPriceTask.setTemporaryUpgrade(0);
                }
                if (!isSameDay(expediaQueryPriceTask.getUpdateTime(), expediaQueryPriceTask.getLastTime())) {
                    expediaQueryPriceTask.setQueryCount(1);
                }
                //更新查询次数
                expediaQueryPriceTaskMapper.updateAddCount(expediaQueryPriceTask);

                long start = System.currentTimeMillis();
                //checkin和checkin+1每组都进行查询价格 比如:2025-03-01到2025-03-30拆分成2025-03-01到2025-03-02、2025-03-02到2025-03-03等
                LocalDate currentCheckin = LocalDate.now().plusDays(expediaQueryPriceTask.getDelayCheckIn());
                LocalDate currentCheckout = LocalDate.now().plusDays(expediaQueryPriceTask.getDelayCheckOut());
                log.info("expediaQueryPriceTask hId: {},currentCheckin:{},currentCheckout:{}  ", expediaQueryPriceTask.getShId(), currentCheckin.toString(), currentCheckout.toString());
                rateLimiter.acquire();
                Monitor.recordOne("expedia_cps_query_price_qps_" + priority);
                PriceReq request = PriceReq.builder().adultNum(2).childNum(0).guestType(0).childAges(new ArrayList<>()).checkIn(currentCheckin.toString()).checkout(currentCheckout.toString()).roomNum(1).build();
                Supplier supplier = Supplier.builder().sHotelId(expediaQueryPriceTask.getShId()).build();
                List<ProductRespDTO> productRespDTOList = expediaPriceService.queryPricesCache(request, supplier);
                log.info("expediaQueryPriceTask productRespDTOList:{}", JSON.toJSONString(productRespDTOList));
                log.info("expediaQueryPriceTask{} query time:{}", priority, System.currentTimeMillis() - start);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("expediaQueryPriceTask queryPricesCache error, hId={}:", expediaQueryPriceTask.getShId(), e);
            }
        }
        // 结束日志：无此行则日志上无法区分「本轮正常跑完」与「中途进程被杀」
        log.info("expediaQueryPriceTask 本轮结束, trigger={}, 成功 {} 行, 失败 {} 行, 共 {} 行, 耗时 {} ms",
                trigger, succeeded, failed, list.size(), System.currentTimeMillis() - roundStart);
        return true;
    }

    public static boolean isSameDay(Date updateTime, Date lastTime) {
        // 新任务行 last_time 为空(从未刷过价)，视为非同一天，走首次/新一天的计数重置
        if (null == updateTime || null == lastTime) {
            return false;
        }
        // 将 Date 转换为 LocalDate
        LocalDate updateLocalDate = updateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate lastLocalDate = lastTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        // 判断是否是同一天
        return updateLocalDate.isEqual(lastLocalDate);
    }


}
