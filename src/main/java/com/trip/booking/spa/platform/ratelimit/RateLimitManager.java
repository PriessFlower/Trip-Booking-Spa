package com.trip.booking.spa.platform.ratelimit;

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

    /**
     * 该 key 是否被显式登记过额度。用途子桶据此决定"扣一格"还是"整格跳过"——
     * 未登记即不分配，只受接口桶约束。不可用 qps 取值判断：未登记会回落 default-qps，
     * 于是"忘了配子桶"会表现成"子桶有个很大的额度"，比不设子桶更糟。
     */
    boolean isRegistered(String key);
}
