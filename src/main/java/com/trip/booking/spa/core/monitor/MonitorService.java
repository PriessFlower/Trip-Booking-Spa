package com.trip.booking.spa.core.monitor;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MonitorService implements MeterBinder {

    private MeterRegistry meterRegistry;

    public Counter getCounter(String counterName) {
        return getCounter(counterName, ImmutableMap.<String, Object>of());
    }

    public Counter getCounter(String counterName, Map<String, Object> tags) {
        if (tags == null) {
            return getCounter(counterName);
        }
        return meterRegistry
                .counter(counterName,
                        tags.entrySet()
                                .stream()
                                .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                                .collect(Collectors.toList()));
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        this.meterRegistry = registry;
    }


    public Timer getTimer(String timerName) {
        return getTimer(timerName, ImmutableMap.<String, Object>of());
    }

    public Timer getTimer(String timerName, Map<String, Object> tags) {
        if (tags == null) {
            return getTimer(timerName);
        }
        return meterRegistry.timer(timerName, tags.entrySet()
                .stream()
                .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                .collect(Collectors.toList()));
    }

    public DistributionSummary getSummary(String name, Map<String, Object> tags) {
        if (tags == null) {
            return getSummary(name);
        }
        List<Tag> tagList = tags.entrySet()
                .stream()
                .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                .collect(Collectors.toList());
        tagList.add(Tag.of("avg_label", name + "_sum/" + name + "_count"));
        return meterRegistry.summary(name, tagList);
    }

    public DistributionSummary getSummary(String name) {
        return getSummary(name, ImmutableMap.of());
    }

    public <T extends Number> T gauge(String gaugeName, T value) {
        return gauge(gaugeName, ImmutableMap.<String, Object>of(), value);
    }

    public <T extends Number> T gauge(String gaugeName, Map<String, Object> tags, T value) {
        return meterRegistry.gauge(gaugeName, tags.entrySet()
                .stream()
                .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                .collect(Collectors.toList()), value);
    }

    public static String obj2Str(Object value) {
        return value == null ? "" : value.toString();
    }

}