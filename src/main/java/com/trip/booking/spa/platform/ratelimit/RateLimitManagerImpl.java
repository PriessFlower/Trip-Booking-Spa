package com.trip.booking.spa.platform.ratelimit;

import com.google.common.util.concurrent.RateLimiter;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流中枢实现：按 key 懒加载缓存限流器，按配置在本地/分布式间选底层。
 *
 * <ul>
 *   <li>local：Guava 令牌桶，单 JVM 计数，纳秒级；acquire 时按最新配置校准速率（跟随 @RefreshScope 热更新）。</li>
 *   <li>distributed：委托现有 {@link DistributedRateLimiter}（Redisson），跨实例共享配额。</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitManagerImpl implements RateLimitManager {

    private static final RateIntervalUnit UNIT = RateIntervalUnit.SECONDS;
    private static final long WINDOW = 1L;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private DistributedRateLimiter distributedRateLimiter;

    /** 本地模式的桶缓存：key -> Guava RateLimiter */
    private final ConcurrentHashMap<String, RateLimiter> localCache = new ConcurrentHashMap<>();

    @Override
    public void acquire(String key) {
        double qps = properties.qpsOf(key);
        if (properties.isDistributed()) {
            RRateLimiter limiter = distributedRateLimiter.getRateLimiter(key, (long) qps, UNIT, WINDOW);
            limiter.acquire(1);
            return;
        }
        localLimiter(key, qps).acquire();
    }

    @Override
    public boolean tryAcquire(String key) {
        double qps = properties.qpsOf(key);
        int timeoutMs = properties.getAcquireTimeoutMs();
        if (properties.isDistributed()) {
            return distributedRateLimiter.tryAcquire(key, (long) qps, UNIT, WINDOW, timeoutMs / 1000);
        }
        return localLimiter(key, qps).tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 取（或建）本地桶，并把速率校准到最新配置——Nacos 改了 QPS 后下次调用即生效，无需重建。
     */
    private RateLimiter localLimiter(String key, double qps) {
        RateLimiter limiter = localCache.computeIfAbsent(key, k -> RateLimiter.create(qps));
        if (limiter.getRate() != qps) {
            limiter.setRate(qps);
        }
        return limiter;
    }
}
