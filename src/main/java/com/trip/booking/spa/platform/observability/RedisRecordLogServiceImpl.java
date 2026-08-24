package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.platform.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service("redisRecordLogServiceImpl")
@Slf4j
public class RedisRecordLogServiceImpl implements RecordLogService {

    public static final String EXPEDIA_QPS_KEY_PREFIX = "record:expedia:qps:";

    @Autowired
    RedisUtils redisUtils;

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
