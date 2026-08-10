package com.trip.booking.spa.core.api.ratehawk.service.impl;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;
import com.trip.booking.spa.core.api.ratehawk.mapper.RateHawkQueryPriceTaskMapper;
import com.trip.booking.spa.core.api.ratehawk.model.RateHawkQueryPriceTask;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkService;
import com.trip.booking.spa.core.monitor.Monitor;
import com.trip.booking.spa.core.util.DateUtil;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.service.impl
 * @Author: dick_w
 * @CreateTime: 2025-03-10  17:20
 * @Description: rateHawk查价缓存接口实现类
 * @Version: 1.0
 */
@Slf4j
@Service
public class RateHawkCPSQueryPriceServiceImpl implements RateHawkCPSQueryPriceService {

    /**
     * 单轮取任务的上限。运维可调，权威取值由 Nacos 的 task.ratehawk-cps.batch-size 下发；
     * 默认 200 为安全侧从严取值（PROJECT.md §2.3.3），缺配置时刷价变慢但不会突发大量请求。
     * 经 Environment 实时读取，改 Nacos 下一轮调度即生效。
     */
    @Autowired
    private Environment environment;

    @Autowired
    private RateHawkQueryPriceTaskMapper rateHawkQueryPriceTaskMapper;

    @Autowired
    private RateHawkService rateHawkService;

    /**
     * 单次调用只消费一轮：取一批（SQL 按 update_time 升序，条数为 batch-size）、逐行刷完即返回。
     * 结构与退出条件的说明同 {@code ExpediaCPSQueryPriceServiceImpl#queryPriceQueueTask}（PROJECT.md §3.8.2、§3.8.3）。
     */
    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade,RateLimiter rateLimiter) {
        int batchSize = environment.getProperty("task.ratehawk-cps.batch-size", Integer.class, 200);
        List<RateHawkQueryPriceTask> list = rateHawkQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade, batchSize);
        log.info("ratehawkQueryPriceTask 本轮取到 {} 行, priority={}, batchSize={}", list.size(), priority, batchSize);
        if (CollectionUtils.isEmpty(list)) {
            return true;
        }
        for (RateHawkQueryPriceTask rateHawkQueryPriceTask : list) {
            // 单行处理整体入 try：坏数据只跳过本行，不影响同批其余行
            try {
                //升级期限为空视为已过期
                Date upgradeDeadline = rateHawkQueryPriceTask.getUpgradeDeadline();
                if (null == upgradeDeadline || !upgradeDeadline.after(new Date())) {
                    rateHawkQueryPriceTask.setTemporaryUpgrade(0);
                }
                if (!isSameDay(rateHawkQueryPriceTask.getUpdateTime(), rateHawkQueryPriceTask.getLastTime())) {
                    rateHawkQueryPriceTask.setQueryCount(1);
                }
                //更新查询次数
                rateHawkQueryPriceTaskMapper.updateAddCount(rateHawkQueryPriceTask);

                long start = System.currentTimeMillis();
                //checkin和checkin+1每组都进行查询价格 比如:2025-03-01到2025-03-30拆分成2025-03-01到2025-03-02、2025-03-02到2025-03-03等
                for(int i = 0; i<rateHawkQueryPriceTask.getDelayCheckOut()-rateHawkQueryPriceTask.getDelayCheckIn(); i++){
                    LocalDate currentCheckin = LocalDate.now().plusDays(i);
                    LocalDate currentCheckout = currentCheckin.plusDays(1);
                    log.info("ratehawkQueryPriceTask hId: {},currentCheckin:{},currentCheckout:{}  ",
                            rateHawkQueryPriceTask.getShId(), currentCheckin.toString(),currentCheckout.toString());
                    rateLimiter.acquire();
                    Monitor.recordOne("ratehawk_cps_query_price_qps_" + priority);
                    PriceReq request = PriceReq.builder().adultNum(1)
                            .childNum(0).guestType(0).childAges(new ArrayList<>())
                            .checkIn(currentCheckin.toString()).checkout(currentCheckout.toString()).roomNum(1).build();
                    Supplier supplier = Supplier.builder().sHotelId(rateHawkQueryPriceTask.getShId()).build();
                    List<ProductRespDTO> productRespDTOList = rateHawkService.queryPricesCache(request, supplier);
                    log.info("ratehawkQueryPriceTask productRespDTOList:{}", JSON.toJSONString(productRespDTOList));
                }
                log.info("ratehawkQueryPriceTask{} query time:{}", priority, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("ratehawkQueryPriceTask queryPricesCache error, hId={}:", rateHawkQueryPriceTask.getShId(), e);
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

    /**
     * @description:根据checkIn和checkOut分组。且每组shId必然不一样、且每组最大只有5条数据，且所有组的元素个数加起来等于原始集合个数(不会丢数据)
     * @author: dick_w
     * @date: 2025/3/10 18:19
     * @param: [queues]
     * @return: java.util.List<java.util.List<com.trip.booking.spa.core.api.ratehawk.model.RateHawkQueryPriceTask>>
     **/
    public static List<List<RateHawkQueryPriceTask>> groupRateHawkQueryPriceTask(List<RateHawkQueryPriceTask> queues) {
        Map<String, List<RateHawkQueryPriceTask>> groupedByCheckInOut = queues.stream()
                .collect(Collectors.groupingBy(q -> q.getDelayCheckIn() + "-" + q.getDelayCheckOut()));

        List<List<RateHawkQueryPriceTask>> result = new ArrayList<>();

        for (List<RateHawkQueryPriceTask> group : groupedByCheckInOut.values()) {
            Collections.sort(group, Comparator.comparing(RateHawkQueryPriceTask::getShId));

            List<RateHawkQueryPriceTask> currentGroup = new ArrayList<>();
            Set<String> shIdSet = new HashSet<>();
            for (RateHawkQueryPriceTask queue : group) {
                if (!shIdSet.contains(queue.getShId())) {
                    currentGroup.add(queue);
                    shIdSet.add(queue.getShId());
                    if (currentGroup.size() == 5) {
                        result.add(new ArrayList<>(currentGroup));
                        currentGroup.clear();
                        shIdSet.clear();
                    }
                }
            }
            if (!currentGroup.isEmpty()) {
                result.add(currentGroup);
            }
        }

        return result;
    }
}
