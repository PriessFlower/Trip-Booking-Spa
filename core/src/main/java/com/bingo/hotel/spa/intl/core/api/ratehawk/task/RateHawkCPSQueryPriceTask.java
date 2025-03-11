package com.bingo.hotel.spa.intl.core.api.ratehawk.task;

import com.alibaba.schedulerx.worker.domain.JobContext;
import com.alibaba.schedulerx.worker.processor.JavaProcessor;
import com.alibaba.schedulerx.worker.processor.ProcessResult;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @BelongsProject: supplier-product-adapter-intl
 * @BelongsPackage: com.bingo.hotel.spa.intl.core.api.ratehawk.task
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

    //qps限流2.5
    RateLimiter rateLimiter = RateLimiter.create(2.5);

    @Override
    public ProcessResult process(JobContext context) {

        log.info("RateHawkCPSQueryPriceTask start");

        rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0,
                rateLimiter);

        return new ProcessResult(true);
    }

}