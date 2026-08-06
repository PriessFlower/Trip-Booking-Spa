package com.trip.booking.spa.core.api.expedia.task;

import com.alibaba.schedulerx.worker.domain.JobContext;
import com.alibaba.schedulerx.worker.processor.JavaProcessor;
import com.alibaba.schedulerx.worker.processor.ProcessResult;
import com.trip.booking.spa.core.api.expedia.service.ExpediaCPSQueryPriceService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.task
 * @Author: dick_w
 * @CreateTime: 2025-03-17  14:22
 * @Description: expedia价格缓存定时任务
 * @Version: 1.0
 */
@Slf4j
@Component
public class ExpediaCPSQueryPriceTask extends JavaProcessor {

    @Autowired
    private ExpediaCPSQueryPriceService expediaCPSQueryPriceService;

    //qps限流
    @Value("${expedia.query.price.cache.qps}")
    private Double cacheQps;

    @Override
    public ProcessResult process(JobContext context) {

        log.info("ExpediaCPSQueryPriceTask start");

        RateLimiter rateLimiter = RateLimiter.create(cacheQps);
        expediaCPSQueryPriceService.queryPriceQueueTask(0, 0, rateLimiter);

        return new ProcessResult(true);
    }

}
