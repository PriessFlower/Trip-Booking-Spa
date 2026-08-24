package com.trip.booking.spa.platform.observability;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉住一个已发生的观测缺陷：gauge 反复上报时<b>只记得第一次</b>。
 *
 * <p>成因是 Micrometer 的 {@code meterRegistry.gauge(name, tags, Number)} 只<b>弱引用</b>传进去的
 * 那个 {@code Number}。而 {@code Integer} 不可变，于是第二次调用既改不了旧实例、又因同名同标签
 * 已存在而被忽略——指标永远停在首次的值上；那个 {@code Integer} 被 GC 之后还会变成 NaN。
 *
 * <p>改动前唯一的 gauge 使用点是文件下载字节数（{@code supplier_file_bytes}），所以那个指标
 * 一直只记得进程内第一次下载的大小。2026-08-25 给刷价加轮内进度 gauge 时发现，一并修掉。
 *
 * <p>修法是注册一次、之后只改持有者的值（{@code AtomicInteger} + 观测函数）。本测试直接
 * 断言"改了值就要能读到新值"，而不是断言实现细节——换实现只要满足这条即可。
 */
class GaugeMustBeUpdatableTest {

    private static final String NAME = "refresh_inflight_done_value";

    @Test
    @DisplayName("同名同标签反复设值，必须读到最新值")
    void repeatedSetsAreVisible() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MonitorService service = new MonitorService();
        service.bindTo(registry);
        Map<String, Object> tags = ImmutableMap.of("supplier", "ELONG", "priority", "0");

        service.setGauge(NAME, tags, 0);
        assertEquals(0d, valueOf(registry, tags), "首次设值就该能读到");

        service.setGauge(NAME, tags, 137);
        assertEquals(137d, valueOf(registry, tags),
                "第二次设值读不到，就是那个「gauge 只记得第一次」的坑——"
                        + "轮内进度会永远停在 0，面板上看着像没在跑");

        service.setGauge(NAME, tags, 6000);
        assertEquals(6000d, valueOf(registry, tags), "第三次同理");

        service.setGauge(NAME, tags, 0);
        assertEquals(0d, valueOf(registry, tags),
                "轮末归零必须生效，否则两轮之间面板停在「已处理=满」，看着像一直在跑");
    }

    @Test
    @DisplayName("标签不同即不同序列，互不覆盖")
    void differentTagsAreSeparateSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MonitorService service = new MonitorService();
        service.bindTo(registry);
        Map<String, Object> p0 = ImmutableMap.of("supplier", "ELONG", "priority", "0");
        Map<String, Object> p1 = ImmutableMap.of("supplier", "ELONG", "priority", "1");

        service.setGauge(NAME, p0, 100);
        service.setGauge(NAME, p1, 200);

        assertEquals(100d, valueOf(registry, p0), "档 0 的进度被档 1 覆盖了——持有者必须按 name+tags 分开");
        assertEquals(200d, valueOf(registry, p1));
    }

    @Test
    @DisplayName("经 Monitor 的门面同样可更新")
    void facadeIsAlsoUpdatable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MonitorService service = new MonitorService();
        service.bindTo(registry);
        new Monitor().setMonitorService(service);
        Map<String, Object> tags = ImmutableMap.of("supplier", "ELONG", "priority", "0");

        // 业务代码走的是 Monitor.recordValue，门面会补 _value 后缀
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, tags, 42);
        assertEquals(42d, valueOf(registry, tags));
        Monitor.recordValue(MetricNames.REFRESH_INFLIGHT_DONE, tags, 43);
        assertEquals(43d, valueOf(registry, tags), "门面透传后仍须可更新");
    }

    private static double valueOf(SimpleMeterRegistry registry, Map<String, Object> tags) {
        Gauge gauge = registry.find(NAME)
                .tag("supplier", String.valueOf(tags.get("supplier")))
                .tag("priority", String.valueOf(tags.get("priority")))
                .gauge();
        assertNotNull(gauge, "gauge 没注册上");
        return gauge.value();
    }
}
