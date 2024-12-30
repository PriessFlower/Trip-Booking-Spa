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
public class GetSupplierHotelTask extends JavaProcessor {

    @Autowired
    private ExpediaStaticInfoService expediaStaticInfoService;

    @Override
    public ProcessResult process(JobContext context) {

        log.info("GetSupplierHotelTask is start!");

        try {
            HashMap<String, String> parametersMap = JsonUtils.readValue(context.getJobParameters(), HashMap.class);
            if (null == parametersMap.get("supplierId")) {
                log.info("GetSupplierHotelTask ,supplierId non-null");
                return new ProcessResult(true);
            }
            //供应商id
            Integer supplierId = Integer.parseInt(parametersMap.get("supplierId").toString());
//            //重跑频率 单位 天 默认一天
//            Integer frequency = null == parametersMap.get("frequency") ? 1 : Integer.parseInt(parametersMap.get("orderStatus").toString());
            switch (supplierId) {
                case 10005:
                    int updateDays = null == parametersMap.get("updateDays") ? 1 : Integer.parseInt(parametersMap.get("updateDays").toString());
                    expediaStaticInfoService.saveOrUpdateHotelInfo(true, false, updateDays, null, 0);
                    break;
                default:
                    log.info("GetSupplierHotelTask ,supplierId:{} no method", supplierId);
            }
        } catch (Exception e) {
            log.error("GetSupplierHotelTask ,error:", e);
        }

        log.info("GetSupplierHotelTask is end!");

        return new ProcessResult(true);
    }
}
