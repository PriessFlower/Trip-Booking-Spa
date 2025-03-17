package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.expedia.mapper.ExpediaQueryPriceTaskMapper;
import com.bingo.hotel.spa.intl.core.api.expedia.model.ExpediaQueryPriceTask;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaCPSQueryPriceService;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaPriceService;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.monitor.Monitor;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @BelongsProject: supplier-product-adapter-intl
 * @BelongsPackage: com.bingo.hotel.spa.intl.core.api.ratehawk.service.impl
 * @Author: dick_w
 * @CreateTime: 2025-03-17  14:24
 * @Description: rateHawk查价缓存service实现类
 * @Version: 1.0
 */
@Slf4j
@Service
public class ExpediaCPSQueryPriceServiceImpl implements ExpediaCPSQueryPriceService {

    @Autowired
    private ExpediaQueryPriceTaskMapper expediaQueryPriceTaskMapper;

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Value("${expedia.query.price.queue.task.switch}")
    private Integer QueryPriceQueueSwitch;

    @Value("${expedia.query.price.queue.task.return}")
    private Integer QueryPriceQueueReturn;

    @Override
    public Boolean queryPriceQueueTask(int priority, int temporaryUpgrade,RateLimiter rateLimiter) {

        while (true) {
            try {
                if (QueryPriceQueueReturn == 0){
                    return true;
                }
                while (true) {
                    if (QueryPriceQueueReturn == 0) {
                        return true;
                    }
                    // 按照更新时间早的取
                    List<ExpediaQueryPriceTask> list = expediaQueryPriceTaskMapper.getQueryPriceTaskList(priority, temporaryUpgrade);

                    log.info("expediaQueryPriceTask list: " + list.size());

                    if (CollectionUtils.isEmpty(list)) {
                        break;
                    }
                    for (ExpediaQueryPriceTask expediaQueryPriceTask : list) {

                        if (!expediaQueryPriceTask.getUpgradeDeadline().after(new Date())) {
                            expediaQueryPriceTask.setTemporaryUpgrade(0);

                        }
                        if (!isSameDay(expediaQueryPriceTask.getUpdateTime(), expediaQueryPriceTask.getLastTime())) {
                            expediaQueryPriceTask.setQueryCount(1);
                        }
                        //更新查询次数
                        expediaQueryPriceTaskMapper.updateAddCount(expediaQueryPriceTask);

                        try {
                            long start = System.currentTimeMillis();
                            List<ProductRespDTO> productRespDTOList;
                            PriceReq request;
                            Supplier supplier;
                            //checkin和checkin+1每组都进行查询价格 比如:2025-03-01到2025-03-30拆分成2025-03-01到2025-03-02、2025-03-02到2025-03-03等
                            for(int i = 0; i<expediaQueryPriceTask.getDelayCheckOut()-expediaQueryPriceTask.getDelayCheckIn(); i++){
                                LocalDate currentCheckin = LocalDate.now().plusDays(i);
                                LocalDate currentCheckout = currentCheckin.plusDays(1);
                                log.info("expediaQueryPriceTask hId: {},currentCheckin:{},currentCheckout:{}  ",
                                        expediaQueryPriceTask.getShId(), currentCheckin.toString(),currentCheckout.toString());
                                rateLimiter.acquire();
                                Monitor.recordOne("expedia_cps_query_price_qps_" + priority);
                                if (QueryPriceQueueSwitch == 1) {
                                    request = PriceReq.builder().adultNum(2)
                                            .childNum(0).guestType(0).childAges(new ArrayList<>())
                                            .checkIn(currentCheckin.toString()).checkout(currentCheckout.toString()).roomNum(1).build();
                                    supplier = Supplier.builder().sHotelId(expediaQueryPriceTask.getShId()).build();
                                    productRespDTOList = expediaPriceService.queryPricesCache(request, supplier);
                                    log.info("expediaQueryPriceTask productRespDTOList:{}", JSON.toJSONString(productRespDTOList));
                                }
                            }
                            log.info("expediaQueryPriceTask{} query time:{}", priority, System.currentTimeMillis() - start);
                        } catch (Exception e) {
                            log.error("expediaQueryPriceTask queryPricesCache error:", e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("expediaQueryPriceTask循环中断，重新开始 error: ", e);
            }
        }
    }

    public static boolean isSameDay(Date updateTime, Date lastTime) {
        // 将 Date 转换为 LocalDate
        LocalDate updateLocalDate = updateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate lastLocalDate = lastTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        // 判断是否是同一天
        return updateLocalDate.isEqual(lastLocalDate);
    }


}