package com.bingo.hotel.spa.intl.core.task;

import com.alibaba.schedulerx.worker.domain.JobContext;
import com.alibaba.schedulerx.worker.processor.JavaProcessor;
import com.alibaba.schedulerx.worker.processor.ProcessResult;
import com.bingo.hotel.base.intl.cli.utils.JsonUtils;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaStaticInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * 获取供应商酒店任务.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/15
 * @since : 1.0
 **/
@Slf4j
@Component
public class ExpediaRemoveHotelTask extends JavaProcessor {

    @Autowired
    private ExpediaStaticInfoService expediaStaticInfoService;

    @Override
    public ProcessResult process(JobContext context) {

        log.info("ExpediaRemoveHotelTask is start!");

        try {
            HashMap<String, String> parametersMap = JsonUtils.readValue(context.getJobParameters(), HashMap.class);
            if (null == parametersMap.get("supplierId")) {
                log.info("GetSupplierHotelTask ,supplierId non-null");
                return new ProcessResult(true);
            }
            String deleteDate = null == parametersMap.get("deleteDate") ? "" : parametersMap.get("deleteDate").toString();
            expediaStaticInfoService.deleteHotelInfo(deleteDate);
        } catch (Exception e) {
            log.error("ExpediaRemoveHotelTask ,error:", e);
        }

        log.info("ExpediaRemoveHotelTask is end!");

        return new ProcessResult(true);
    }
}
