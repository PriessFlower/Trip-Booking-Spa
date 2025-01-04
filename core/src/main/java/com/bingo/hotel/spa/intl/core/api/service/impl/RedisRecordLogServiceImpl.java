package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.core.api.service.RecordLogService;
import com.bingo.hotel.spa.intl.core.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service("redisRecordLogServiceImpl")
@Slf4j
public class RedisRecordLogServiceImpl implements RecordLogService {

    public static final String DAOLV_QPS_KEY_PREFIX = "record:daolv:qps:";
    public static final String AICHOTELS_QPS_KEY_PREFIX = "record:aichotels:qps:";
    public static final String TRAVELCONNECT_QPS_KEY_PREFIX = "record:travelconnect:qps:";
    public static final String HUITRAVEL_QPS_KEY_PREFIX = "record:huitravel:qps:";
    public static final String EXPEDIA_QPS_KEY_PREFIX = "record:expedia:qps:";

    @Autowired
    RedisUtils redisUtils;

    @Override
    public void recordDaolvQps() {
        recordQpsByHour(DAOLV_QPS_KEY_PREFIX);
    }

    @Override
    public void recordAichotelsQps() {
        recordQpsByHour(AICHOTELS_QPS_KEY_PREFIX);
    }

    @Override
    public void recordTravelconnectQps() {
        recordQpsByHour(TRAVELCONNECT_QPS_KEY_PREFIX);
    }

    @Override
    public void recordHuiTravelQps() {
        recordQpsByHour(HUITRAVEL_QPS_KEY_PREFIX);
    }

    @Override
    public void recordExpediaQps() {
        recordQpsByHour(EXPEDIA_QPS_KEY_PREFIX);
    }

    public void recordQpsByHour(String key) {
        try {
            Date nowDate = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd&HH");
            String dateFormat = sdf.format(nowDate);
            String[] dateFormatSplit = dateFormat.split("&");
            String date = dateFormatSplit[0];
            String time = dateFormatSplit[1];
            redisUtils.incr(key + date + ":" + time);
        } catch (Exception e) {
            log.error("记录qps异常", e);
        }

    }
}
