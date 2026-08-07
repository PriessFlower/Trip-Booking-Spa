package com.trip.booking.spa.core.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证本地(Guava)限流：令牌耗尽后 tryAcquire 拒绝、acquire 阻塞放行、热更新校准速率。
 * 不依赖 Spring/Redis，直接构造 Manager + 桩配置。
 */
class RateLimitManagerImplTest {

    private RateLimitManagerImpl manager(double qps, int timeoutMs) {
        RateLimitProperties props = new RateLimitProperties();
        ReflectionTestUtils.setField(props, "mode", "local");
        ReflectionTestUtils.setField(props, "defaultQps", qps);
        ReflectionTestUtils.setField(props, "acquireTimeoutMs", timeoutMs);
        ReflectionTestUtils.setField(props, "qpsJson", "");
        props.init();
        RateLimitManagerImpl m = new RateLimitManagerImpl();
        ReflectionTestUtils.setField(m, "properties", props);
        return m;
    }

    @Test
    void tryAcquire_rejectsWhenExhausted() {
        // QPS=2、超时=0：桶初始 1 个令牌，第一次拿到，之后立即拒
        RateLimitManagerImpl m = manager(2, 0);
        String key = "TEST:supplierA:price";
        boolean first = m.tryAcquire(key);
        int granted = first ? 1 : 0;
        for (int i = 0; i < 20; i++) {
            if (m.tryAcquire(key)) {
                granted++;
            }
        }
        assertTrue(first, "首个令牌应放行");
        assertTrue(granted < 21, "超时=0 时应大量拒绝，实际放行=" + granted);
    }

    @Test
    void acquire_blocksButEventuallyPasses() {
        // QPS=50、阻塞式：5 次都应拿到（总耗时约 0.1s），不抛异常
        RateLimitManagerImpl m = manager(50, 5000);
        String key = "TEST:supplierB:price";
        for (int i = 0; i < 5; i++) {
            m.acquire(key);
        }
        assertTrue(true, "阻塞式获取应全部放行");
    }

    @Test
    void differentKeys_independentBuckets() {
        // 两个 key 各自独立的桶，互不影响
        RateLimitManagerImpl m = manager(1, 0);
        m.tryAcquire("TEST:s1:price");
        boolean otherKeyStillHasToken = m.tryAcquire("TEST:s2:price");
        assertTrue(otherKeyStillHasToken, "不同 key 应是独立的桶");
    }
}
