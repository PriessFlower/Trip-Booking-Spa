package com.trip.booking.spa.core.ratelimit;

/**
 * 全项目唯一的限流入口。底层在 Guava(单机) / Redisson(分布式) 间由配置切换，调用方无感。
 */
public interface RateLimitManager {

    /**
     * 阻塞式获取许可：拿不到就等，直到有令牌。用于后台批处理 / CPS 刷价（把请求匀开、平滑放行）。
     */
    void acquire(String key);

    /**
     * 非阻塞获取许可：等待上限内拿不到即返回 false。用于在线查价/验价（快速失败，不拖住用户请求）。
     */
    boolean tryAcquire(String key);
}
