package com.trip.booking.spa.gateway.adapter.outbound.state.offer;

import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import com.trip.booking.spa.platform.redis.RedisUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 句柄生命周期三态必须可数：签发（offer_issued{supplier}）、取回落空
 * （offer_resolve_miss，上游拿陈句柄下单的直接信号）、核销（offer_consumed）。
 *
 * <p>miss 是 TTL 按家收紧（R-2.2 接线）后的观察指标：收得过短，miss 率先涨。
 * 此前三处都只有日志，「上游多久拿一次陈句柄」这个问题在指标通道上无解。
 */
class OfferStoreMetricsTest {

    private OfferStore store;
    private RedisUtils redis;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        store = new OfferStore();
        redis = Mockito.mock(RedisUtils.class);
        ReflectionTestUtils.setField(store, "redisUtils", redis);
        ReflectionTestUtils.setField(store, "ttlSeconds", 600L);

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
    @DisplayName("签发成功 → offer_issued{supplier=ELONG} 计一次；写入失败不计")
    void issueIsCountedOnlyOnSuccess() {
        Mockito.when(redis.setex(anyString(), anyString(), anyLong())).thenReturn(true);
        store.issue(10010, Map.of("k", "v"));
        assertEquals(1.0, registry.counter("offer_issued_count", "supplier", "ELONG").count());

        Mockito.when(redis.setex(anyString(), any(String.class), anyLong())).thenReturn(false);
        store.issue(10010, Map.of("k", "v"));
        assertEquals(1.0, registry.counter("offer_issued_count", "supplier", "ELONG").count());
    }

    @Test
    @DisplayName("取回落空（不存在/过期/内容坏）→ offer_resolve_miss 各计一次")
    void resolveMissIsCounted() {
        Mockito.when(redis.get(anyString())).thenReturn("");
        store.resolve("of_gone");
        assertEquals(1.0, registry.counter("offer_resolve_miss_count").count());

        Mockito.when(redis.get(anyString())).thenReturn("{\"supplierId\":null}");
        store.resolve("of_broken");
        assertEquals(2.0, registry.counter("offer_resolve_miss_count").count());
    }

    @Test
    @DisplayName("取回成功不计 miss")
    void successfulResolveIsNotAMiss() {
        Mockito.when(redis.get(anyString()))
                .thenReturn("{\"supplierId\":10010,\"credentials\":{\"k\":\"v\"},\"expiresAt\":1}");
        store.resolve("of_alive");
        assertEquals(0.0, registry.counter("offer_resolve_miss_count").count());
    }

    @Test
    @DisplayName("核销 → offer_consumed 计一次")
    void consumeIsCounted() {
        store.consume("of_done");
        assertEquals(1.0, registry.counter("offer_consumed_count").count());
    }
}
