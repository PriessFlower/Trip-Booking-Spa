package com.trip.booking.spa.core.api.ratehawk.task;

import com.alibaba.schedulerx.worker.domain.JobContext;
import com.alibaba.schedulerx.worker.processor.JavaProcessor;
import com.alibaba.schedulerx.worker.processor.ProcessResult;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.task
 * @Author: dick_w
 * @CreateTime: 2025-03-10  16:59
 * @Description: ratehawk价格缓存定时任务
 * @Version: 1.0
 */
@Slf4j
@Component
public class RateHawkCPSQueryPriceTask extends JavaProcessor {

    @Autowired
    private RateHawkCPSQueryPriceService rateHawkCPSQueryPriceService;

    //qps限流 生产环境2.5  测试环境约0.16（1分钟10次）
    @Value("${ratehawk.query.price.cache.qps}")
    private Double cacheQps;

    @Override
    public ProcessResult process(JobContext context) {

        log.info("RateHawkCPSQueryPriceTask start");

        RateLimiter rateLimiter = RateLimiter.create(cacheQps);
        rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0, rateLimiter);

        return new ProcessResult(true);
    }

}
