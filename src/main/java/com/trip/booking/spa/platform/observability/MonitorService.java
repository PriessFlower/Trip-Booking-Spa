package com.trip.booking.spa.platform.observability;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class MonitorService implements MeterBinder {

    private MeterRegistry meterRegistry;

    /** 每个 name+tags 一个可变持有者：gauge 注册一次、之后只改值（见 setGauge 的注释） */
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> gaugeHolders =
            new java.util.concurrent.ConcurrentHashMap<>();

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

    /**
     * 耗时类 summary 的直方图桶（毫秒）。没有桶就没有 p90/p99——summary 在 Prometheus 里
     * 只有 sum/count，均值会被长尾抹平（一次 10s 的超时摊进一百次 50ms 里只抬 100ms）。
     * 桶是固定档而非 percentile 预聚合：histogram_quantile 可跨标签聚合（按 interface
     * 合并各 status），客户端分位数不能。八档覆盖 50ms～10s，供应商接口耗时都落在这个区间。
     */
    private static final double[] TIME_SLO_MS = {50, 100, 250, 500, 1000, 2500, 5000, 10000};

    public DistributionSummary getSummary(String name, Map<String, Object> tags) {
        if (tags == null) {
            return getSummary(name);
        }
        List<Tag> tagList = tags.entrySet()
                .stream()
                .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                .collect(Collectors.toList());
        tagList.add(Tag.of("avg_label", name + "_sum/" + name + "_count"));
        return DistributionSummary.builder(name)
                .tags(tagList)
                .serviceLevelObjectives(TIME_SLO_MS)
                .register(meterRegistry);
    }

    public DistributionSummary getSummary(String name) {
        return getSummary(name, ImmutableMap.of());
    }

    /**
     * 设置一个<b>可反复更新</b>的 gauge。
     *
     * <p>不能直接用 {@code meterRegistry.gauge(name, tags, Integer)}：Micrometer 只弱引用传进去的
     * {@code Number} 实例，而 {@code Integer} 是不可变的——同名同标签第二次注册被忽略，于是 gauge
     * 永远停在第一次的值上；那个 {@code Integer} 被 GC 之后更会变成 NaN。改动前 gauge 就是这么用的，
     * 唯一的使用点是文件下载字节数（{@code supplier_file_bytes}），所以那个指标其实一直只记得第一次。
     *
     * <p>正确做法是注册一次、之后只改持有者的值。每个 name+tags 一个 {@link AtomicInteger}，
     * gauge 观测它，故 Prometheus 每次抓取拿到的都是当前值。
     */
    public void setGauge(String gaugeName, Map<String, Object> tags, int value) {
        Map<String, Object> safeTags = tags == null ? ImmutableMap.of() : tags;
        String id = gaugeName + safeTags;
        gaugeHolders.computeIfAbsent(id, key -> {
            AtomicInteger holder = new AtomicInteger();
            meterRegistry.gauge(gaugeName, safeTags.entrySet()
                    .stream()
                    .map(entry -> Tag.of(entry.getKey(), obj2Str(entry.getValue())))
                    .collect(Collectors.toList()), holder, AtomicInteger::get);
            return holder;
        }).set(value);
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