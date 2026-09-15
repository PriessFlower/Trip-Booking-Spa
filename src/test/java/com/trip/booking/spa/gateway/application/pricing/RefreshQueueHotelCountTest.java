package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 清单覆盖面这两个 gauge（{@code refresh_queue_hotels} / {@code refresh_queue_onsale_hotels}）
 * 的口径守卫。
 *
 * <p>为什么值得守：按行与按家会给出相反的印象（前者近乎全绿、后者大半没货），覆盖面的判断
 * 全押在后者的口径上。基线数字见 docs/price-refresh.md F-2.6。
 */
class RefreshQueueHotelCountTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("两个店数以 supplier 为标签出现，且重采能读到新值")
    void bothCountsArePublishedPerSupplier() {
        RefreshViaQueryTest.StubRefresh flow = new RefreshViaQueryTest.StubRefresh();
        flow.queueHotelCount.setHotels(537);
        flow.queueHotelCount.setOnsaleHotels(81);

        flow.sampleQueueHotels();

        assertEquals(537.0, gauge(MetricNames.REFRESH_QUEUE_HOTELS));
        assertEquals(81.0, gauge(MetricNames.REFRESH_QUEUE_ONSALE_HOTELS));

        // 覆盖面会变，gauge 必须跟着变——「只记得第一次」那个坑见 GaugeMustBeUpdatableTest
        flow.queueHotelCount.setOnsaleHotels(120);
        flow.sampleQueueHotels();
        assertEquals(120.0, gauge(MetricNames.REFRESH_QUEUE_ONSALE_HOTELS));
    }

    private double gauge(String name) {
        return registry.get(name + "_value").tags("supplier", "FLIGGY").gauge().value();
    }

    /**
     * 六家的 SQL 必须同口径。最容易写错的是上界：写成 {@code < 10} 就把<b>人工停用位 9</b>
     * 也算成有货，而艺龙与飞猪在这一位上都有存量行。
     */
    @Test
    @DisplayName("六家清单覆盖面的 SQL 口径一致：业务档上界是 9，且刷过才算")
    void allSixQueriesShareTheSameDefinition() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path xml : mapperXmls()) {
            String sql = between(Files.readString(xml), "<select id=\"countQueueHotels\"", "</select>");
            if (sql == null) {
                offenders.add(xml.getFileName() + "：没有 countQueueHotels");
                continue;
            }
            if (!sql.contains("priority_level_number &lt; 9")) {
                offenders.add(xml.getFileName() + "：业务档上界不是 9（停用位 9 会被当成有货）");
            }
            if (!sql.contains("last_time IS NOT NULL")) {
                offenders.add(xml.getFileName() + "：没排除从未刷过的行（新播的清单会虚高成 100%）");
            }
            if (!sql.contains("COUNT(DISTINCT sh_id)")) {
                offenders.add(xml.getFileName() + "：分母不是按家数（行数答不了覆盖面）");
            }
        }
        assertTrue(offenders.isEmpty(), "清单覆盖面的口径漂了：" + offenders);
    }

    private static List<Path> mapperXmls() throws Exception {
        List<Path> xmls = new ArrayList<>();
        for (String family : List.of("Elong", "Fliggy", "Expedia", "Dida", "Clwy", "Meituan")) {
            xmls.add(Path.of("src/main/resources/mapper/" + family + "QueryPriceTaskMapper.xml"));
        }
        return xmls;
    }

    private static String between(String text, String from, String to) {
        int start = text.indexOf(from);
        if (start < 0) {
            return null;
        }
        int end = text.indexOf(to, start);
        return end < 0 ? null : text.substring(start, end);
    }
}
