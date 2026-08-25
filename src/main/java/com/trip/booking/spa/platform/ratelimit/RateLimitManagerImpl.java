package com.trip.booking.spa.platform.ratelimit;

import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 限流中枢：<b>只有一种实现</b>——跨实例的 Redisson 令牌桶，配额来自 Nacos。
 *
 * <p><b>为什么删掉了 local/distributed 这个开关</b>（2026-08-25）：供应商的配额是账号或接口级的，
 * 与我们部署几个实例无关。Guava 在 JVM 内计数，单实例时"本机"恰好等于"全局"——那是侥幸，
 * 而不是设计。加第二台实例就是对供应商双倍流量，且这个错误<b>不会有任何报错</b>，只会表现为
 * 频控变多。
 *
 * <p>更直接的证据是同一条刷价路径上的不一致：分布式锁早就是跨实例的（Redisson RLock，F-2.3），
 * 限流却是单机的。锁的作者考虑了多实例，限流没有。留着 {@code mode} 等于留一个"配错就静默超
 * 配额"的开关，而这两天一直在消除的正是这种「同一件事两个开关」。
 *
 * <p>代价：每次扣格一次 Redis 往返（Lua 脚本，同 VPC 亚毫秒）。刷价无所谓；客流路径加不到 1ms。
 * Redis 不可用时限流器拿不到许可 → 前台快速失败、后台阻塞等待。这是<b>刻意的</b>：放行意味着
 * 对供应商无限流，可能招致封号；而拒绝只是这段时间不刷价。价格缓存本就在同一个 Redis 上，
 * 它挂了刷价与出价都已不可用，故不算新增单点。
 */
@Slf4j
@Component
public class RateLimitManagerImpl implements RateLimitManager {

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private DistributedRateLimiter limiter;

    @Override
    public void acquire(String key) {
        limiter.acquire(key, properties.qpsOf(key));
    }

    @Override
    public boolean tryAcquire(String key) {
        return limiter.tryAcquire(key, properties.qpsOf(key), properties.getAcquireTimeoutMs());
    }

    @Override
    public boolean isRegistered(String key) {
        return properties.isRegistered(key);
    }
}
