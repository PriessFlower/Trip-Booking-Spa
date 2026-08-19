package com.trip.booking.spa.platform.observability;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Created by  on 2019/3/6.
 */
@SuppressWarnings("WeakerAccess")
@Component
public class Monitor {

    private static final Logger log = LoggerFactory.getLogger(Monitor.class);

    private static final String COUNTER_METRIC_SUFFIX = "_count";
    private static final String TIMER_METRIC_SUFFIX = "_time";
    private static final String GAUGE_METRIC_SUFFIX = "_value";

    private static MonitorService monitorService;

    @Autowired
    public void setMonitorService(MonitorService service) {
        if (null == service) {
            log.error("inject MonitorService null");
        }
        Monitor.monitorService = service;
    }

    private static Counter getCounter(String counterName) {
        return getCounter(counterName, ImmutableMap.of());
    }

    private static Counter getCounter(String counterName, Map<String, Object> tags) {
        return null == monitorService ? null : monitorService.getCounter(counterName, tags);
    }

    private static DistributionSummary getSummary(String timerName) {
        return null == monitorService ? null : monitorService.getSummary(timerName);
    }

    private static DistributionSummary getSummary(String timerName, Map<String, Object> tags) {
        return null == monitorService ? null : monitorService.getSummary(timerName, tags);
    }

    private static <T extends Number> T gauge(String gaugeName, T value) {
        return null == monitorService ? null : monitorService.gauge(gaugeName, value);
    }

    private static <T extends Number> T gauge(String gaugeName, Map<String, Object> tags, T value) {
        return null == monitorService ? null : monitorService.gauge(gaugeName, tags, value);
    }

    public static void recordOne(String monitorName) {
        if (null == monitorService) {
            return;
        }
        getCounter(monitorName + COUNTER_METRIC_SUFFIX).increment();
    }

    public static void recordOne(String monitorName, Map<String, Object> tags) {
        if (null == monitorService) {
            return;
        }
        getCounter(monitorName + COUNTER_METRIC_SUFFIX, tags).increment();
    }

    public static void recordTime(String monitorName, long time) {
        if (null == monitorService) {
            return;
        }
        getSummary(monitorName + TIMER_METRIC_SUFFIX).record(time);
    }

    public static void recordTime(String monitorName, Map<String, Object> tags, long time) {
        if (null == monitorService) {
            return;
        }
        getSummary(monitorName + TIMER_METRIC_SUFFIX, tags).record(time);
    }

    public static void recordOne(String monitorName, long time) {
        if (null == monitorService) {
            return;
        }
        recordOne(monitorName);
        recordTime(monitorName, time);
    }

    public static void recordOne(String monitorName, Map<String, Object> tags, long time) {
        if (null == monitorService) {
            return;
        }
        recordOne(monitorName, tags);
        recordTime(monitorName, tags, time);
    }

    public static void recordMany(String monitorName, int count) {
        if (null == monitorService) {
            return;
        }
        getCounter(monitorName + COUNTER_METRIC_SUFFIX).increment(count);
    }

    public static void recordMany(String monitorName, Map<String, Object> tags) {
        if (null == monitorService) {
            return;
        }
        getCounter(monitorName + COUNTER_METRIC_SUFFIX, tags).increment();
    }

    /**
     * 带维度地按量累加。
     *
     * <p>补这个重载，是因为原先只有「无 tag 按量累加」与「带 tag 只加一」两种。想同时做到
     * §3.9.2「维度进 tag、不进名字」和「一个业务周期上报一次总量」，就只能把维度拼进指标名
     * ——那正是要避免的名字爆炸。刷价轮次的三态计数是第一个真实用例。
     */
    public static void recordMany(String monitorName, Map<String, Object> tags, int count) {
        if (null == monitorService) {
            return;
        }
        getCounter(monitorName + COUNTER_METRIC_SUFFIX, tags).increment(count);
    }

    public static void recordValue(String monitorName, int value) {
        if (null == monitorService) {
            return;
        }
        gauge(monitorName + GAUGE_METRIC_SUFFIX, value);
    }

    public static void recordValue(String monitorName, Map<String, Object> tags, int value) {
        if (null == monitorService) {
            return;
        }
        gauge(monitorName + GAUGE_METRIC_SUFFIX, tags, value);
    }


}
