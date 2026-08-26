package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.platform.http.HttpUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采样器把两处注册表的水位推成 gauge：池名/host 是标签不是名字（O-2.1），
 * 消亡的池推一次 0——否则短命池（刷价每轮）的水位会永远冻在最后一个样本上。
 */
class PoolStatsSamplerTest {

    private SimpleMeterRegistry registry;
    private PoolStatsSampler sampler;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
        sampler = new PoolStatsSampler();
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("线程池与连接池水位以 pool/host 为标签出现在注册表里")
    void watermarksArePublishedWithTags() {
        ExecutorService pool = ThreadPools.fixed("sampler-pool", 2, true);
        try {
            HttpUtils.getHttpClient("https://sampler-host.invalid/ping");

            sampler.sample();

            assertEquals(0.0, registry.get("thread_pool_queue_value")
                    .tags("pool", "sampler-pool").gauge().value());
            // 核心线程惰性创建，空池 size=0——断言的是 gauge 存在且标签对，不是池预热
            assertEquals(0.0, registry.get("thread_pool_size_value")
                    .tags("pool", "sampler-pool").gauge().value());
            assertTrue(registry.get("http_pool_max_value")
                    .tags("host", "sampler-host.invalid").gauge().value() > 0);
            assertEquals(0.0, registry.get("http_pool_leased_value")
                    .tags("host", "sampler-host.invalid").gauge().value());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("池消亡后下一轮采样推 0，不让水位冻在最后一个样本上")
    void vanishedPoolIsZeroed() throws Exception {
        ExecutorService pool = ThreadPools.fixed("sampler-vanish", 1, true);
        pool.submit(() -> {
        }).get(2, TimeUnit.SECONDS);
        sampler.sample();
        assertEquals(1.0, registry.get("thread_pool_size_value")
                .tags("pool", "sampler-vanish").gauge().value());

        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        sampler.sample();

        assertEquals(0.0, registry.get("thread_pool_size_value")
                .tags("pool", "sampler-vanish").gauge().value());
    }
}
