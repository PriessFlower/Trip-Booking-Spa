package com.trip.booking.spa.core.api.expedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;
import com.trip.booking.spa.core.api.expedia.mapper.ExpediaQueryPriceTaskMapper;
import com.trip.booking.spa.core.api.expedia.model.ExpediaQueryPriceTask;
import com.trip.booking.spa.core.api.expedia.service.ExpediaCPSQueryPriceService;
import com.trip.booking.spa.core.api.expedia.service.ExpediaPriceService;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkService;
import com.trip.booking.spa.core.monitor.Monitor;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.service.impl
 * @Author: dick_w
 * @CreateTime: 2025-03-17  14:24
 * @Description: rateHawk查价缓存service实现类
 * @Version: 1.0
 */
@Slf4j
@Service
public class ExpediaCPSQueryPriceServiceImpl implements ExpediaCPSQueryPriceService {

    /**
     * 单轮取任务的上限。运维可调，权威取值由 Nacos 的 task.expedia-cps.batch-size 下发；
     * 默认 200 为安全侧从严取值（PROJECT.md §2.3.3），缺配置时刷价变慢但不会突发大量请求。
     * 经 Environment 实时读取，改 Nacos 下一轮调度即生效。
     */
    @Autowired
    private Environment environment;

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
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, RateLimiter rateLimiter) {
        int batchSize = environment.getProperty("task.expedia-cps.batch-size", Integer.class, 200);
        List<ExpediaQueryPriceTask> list = expediaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("expediaQueryPriceTask 本轮取到 {} 行, priority={}, batchSize={}", list.size(), priority, batchSize);
        if (CollectionUtils.isEmpty(list)) {
            return true;
        }
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
            } catch (Exception e) {
                log.error("expediaQueryPriceTask queryPricesCache error, hId={}:", expediaQueryPriceTask.getShId(), e);
            }
        }
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
