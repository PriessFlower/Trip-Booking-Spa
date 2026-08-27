package com.trip.booking.spa.platform.observability;

import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 耗时 summary 必须带直方图桶——没有桶就没有 p90/p99，均值会被长尾抹平
 * （一次 10s 超时摊进一百次 50ms 里只抬 100ms，盘上看不出任何异常）。
 * 看板的 histogram_quantile 依赖 _bucket 序列，桶没了盘就空了，这里钉住。
 */
class TimeSummaryHistogramTest {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("recordTime 产生 8 档 SLO 桶,300ms 落进 500ms 桶")
    void timeSummaryCarriesSloBuckets() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);

        Monitor.recordTime("histogram_probe", Map.of("supplier", "ELONG"), 300);

        HistogramSnapshot snapshot = registry.get("histogram_probe_time")
                .tags("supplier", "ELONG").summary().takeSnapshot();
        assertEquals(8, snapshot.histogramCounts().length, "SLO 桶数变了：看板分位查询须同步");
        // 300ms：≤250 的桶不含它，≤500 起累计计 1
        assertEquals(0, (long) snapshot.histogramCounts()[2].count());
        assertTrue(snapshot.histogramCounts()[3].count() >= 1);
    }
}
