package com.bingo.hotel.spa.intl.core.api.ratehawk.service.impl;

import com.alibaba.fastjson.JSON;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.ratehawk.mapper.RateHawkQueryPriceTaskMapper;
import com.bingo.hotel.spa.intl.core.api.ratehawk.model.RateHawkQueryPriceTask;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.monitor.Monitor;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * @BelongsProject: supplier-product-adapter-intl
 * @BelongsPackage: com.bingo.hotel.spa.intl.core.api.ratehawk.service.impl
 * @Author: dick_w
 * @CreateTime: 2025-03-10  17:20
 * @Description: rateHawk查价缓存接口实现类
 * @Version: 1.0
 */
@Slf4j
@Service
public class RateHawkCPSQueryPriceServiceImpl implements RateHawkCPSQueryPriceService {

    @Autowired
    private RateHawkQueryPriceTaskMapper rateHawkQueryPriceTaskMapper;

    @Autowired
    private RateHawkService rateHawkService;

    @Value("${ratehawk.query.price.queue.task.switch}")
    private Integer QueryPriceQueueSwitch;

    @Value("${ratehawk.query.price.queue.task.return}")
    private Integer QueryPriceQueueReturn;

    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade, RateLimiter rateLimiter) {

//        while (true) {
            try {

                if (QueryPriceQueueReturn == 0){
                    return true;
                }
                while (true) {
                    if (QueryPriceQueueReturn == 0) {
                        return true;
                    }
                    // 按照更新时间早的取
                    List<RateHawkQueryPriceTask> list = rateHawkQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade);

                    log.info("ratehawkQueryPriceTask list: " + list.size());

                    if (CollectionUtils.isEmpty(list)) {
                        break;
                    }
//                  List<List<RateHawkQueryPriceTask>> groupList = groupRateHawkQueryPriceTask(list);
                    for (RateHawkQueryPriceTask rateHawkQueryPriceTask : list) {

                        if (!rateHawkQueryPriceTask.getUpgradeDeadline().after(new Date())) {
                            rateHawkQueryPriceTask.setTemporaryUpgrade(0);

                        }
                        if (!isSameDay(rateHawkQueryPriceTask.getUpdateTime(), rateHawkQueryPriceTask.getLastTime())) {
                            rateHawkQueryPriceTask.setQueryCount(1);
                        }
                        //更新查询次数
                        rateHawkQueryPriceTaskMapper.updateAddCount(rateHawkQueryPriceTask);

                        String checkIn = DateUtil.getDateStr(DateUtil.addDay(new Date(), rateHawkQueryPriceTask.getDelayCheckIn()));
                        String checkOut = DateUtil.getDateStr(DateUtil.addDay(new Date(), rateHawkQueryPriceTask.getDelayCheckOut()));
                        try {
                            long start = System.currentTimeMillis();
                            log.info("ratehawkQueryPriceTask hId: {},checkin:{}  ", rateHawkQueryPriceTask.getShId(), checkIn);
                            rateLimiter.acquire();
                            Monitor.recordOne("ratehawk_cps_query_price_qps_" + priority);
                            if (QueryPriceQueueSwitch == 1) {
                                PriceReq request = PriceReq.builder().adultNum(1)
                                        .childNum(0).guestType(0).childAges(new ArrayList<>())
                                        .checkIn(checkIn).checkout(checkOut).roomNum(1).build();
                                Supplier supplier = Supplier.builder().sHotelId(rateHawkQueryPriceTask.getShId()).build();
                                List<ProductRespDTO> productRespDTOList = rateHawkService.queryPricesCache(request, supplier);
                                log.info("ratehawkQueryPriceTask productRespDTOList:{}", JSON.toJSONString(productRespDTOList));
                            }
                            log.info("ratehawkQueryPriceTask{} query time:{}", priority, System.currentTimeMillis() - start);
                        } catch (Exception e) {
                            log.error("ratehawkQueryPriceTask queryPricesCache error:", e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("ratehawkQueryPriceTask循环中断，重新开始 error: ", e);
            }
//        }
        return true;
    }

    public static boolean isSameDay(Date updateTime, Date lastTime) {
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
     * @return: java.util.List<java.util.List<com.bingo.hotel.spa.intl.core.api.ratehawk.model.RateHawkQueryPriceTask>>
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