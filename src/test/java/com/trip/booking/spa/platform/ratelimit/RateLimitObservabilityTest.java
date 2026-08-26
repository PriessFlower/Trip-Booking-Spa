package com.trip.booking.spa.platform.ratelimit;

import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 限流此前的两个观测盲区，各钉一条：
 *
 * <ol>
 *   <li>漏配桶静默跑 default-qps——桶名是运行期拼出的字符串，两道配置漂移检查都看不见，
 *       实证是 ba3d767 两个桶漏登记而 CI 全绿。现在回落必计
 *       {@code ratelimit_default_qps_fallback&#123;bucket&#125;}，指着名字告诉你哪个桶没配。</li>
 *   <li>后台用途被限流是阻塞等待不抛错——桶配小了表现为刷价静默变慢，与「任务本身慢」
 *       无从区分。现在每次后台取许可记 {@code ratelimit_wait}（count+time）。</li>
 * </ol>
 */
class RateLimitObservabilityTest {

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
        ReflectionTestUtils.setField(RateLimitHolder.class, "manager", null);
    }

    @Test
    @DisplayName("键未登记 → 回落 default-qps 并计 fallback{bucket}；已登记不计")
    void defaultQpsFallbackIsCounted() {
        RateLimitProperties properties = new RateLimitProperties();
        ReflectionTestUtils.setField(properties, "defaultQps", 1.0);

        assertEquals(1.0, properties.qpsOf("GLOBAL_LIMIT:X:MISSING"));
        assertEquals(1.0, registry.counter("ratelimit_default_qps_fallback_count",
                "bucket", "GLOBAL_LIMIT:X:MISSING").count());

        ReflectionTestUtils.setField(properties, "qps",
                Map.of("GLOBAL_LIMIT:X:PRESENT", 5.0));
        assertEquals(5.0, properties.qpsOf("GLOBAL_LIMIT:X:PRESENT"));
        assertEquals(0.0, registry.counter("ratelimit_default_qps_fallback_count",
                "bucket", "GLOBAL_LIMIT:X:PRESENT").count());
    }

    @Test
    @DisplayName("后台用途取许可 → ratelimit_wait{bucket} 计一次；前台不计")
    void backgroundWaitIsCounted() {
        RateLimitManager manager = Mockito.mock(RateLimitManager.class);
        Mockito.when(manager.isRegistered(Mockito.anyString())).thenReturn(false);
        Mockito.when(manager.tryAcquire(Mockito.anyString())).thenReturn(true);
        ReflectionTestUtils.setField(RateLimitHolder.class, "manager", manager);

        Permits.take("GLOBAL_LIMIT:X:API", CallPurpose.REFRESH);
        assertEquals(1.0, registry.counter("ratelimit_wait_count",
                "bucket", "GLOBAL_LIMIT:X:API").count());

        Permits.take("GLOBAL_LIMIT:X:API", CallPurpose.LIVE);
        assertEquals(1.0, registry.counter("ratelimit_wait_count",
                "bucket", "GLOBAL_LIMIT:X:API").count());
    }
}
