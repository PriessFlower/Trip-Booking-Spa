package com.trip.booking.spa.platform.redis;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 跨实例的限流器（Redisson {@link RRateLimiter}，{@link RateType#OVERALL} = 所有客户端共享一份配额）。
 *
 * <p><b>为什么需要它</b>：供应商的配额是账号/接口级的，与我们部署几个实例无关。Guava 在 JVM 内
 * 计数，单实例时"本机"恰好等于"全局"——那是侥幸，加第二台实例就是对供应商双倍流量。而同一条
 * 刷价路径上的分布式锁已经是跨实例的（Redisson RLock），两处设计前提不一致。
 *
 * <p><b>三个已修的缺陷</b>（2026-08-25。此前 {@code ratelimit.mode=local} 从未走到这条路，
 * 故缺陷一直没暴露——切 distributed 之前必须先修，否则是"切了就坏"）：
 * <ol>
 *   <li>{@code trySetRate} 在 Redis 里已有配置时是<b>空操作</b>，于是 Nacos 改配额不生效、
 *       速率冻在第一次设的值上。改用 {@code setRate} 每次校准，与 Guava 路径的
 *       {@code limiter.setRate} 行为一致；</li>
 *   <li>{@code tryAcquire} 收了 timeout 参数却<b>没用</b>，拿不到许可立即返回 false，
 *       {@code ratelimit.acquire-timeout-ms} 形同虚设；</li>
 *   <li>速率被 {@code (long) qps} 截断，0.5 QPS 变成 <b>0</b>——永久阻塞。见
 *       {@link #windowMillisFor(double)}。</li>
 * </ol>
 */
@Component
@Slf4j
public class DistributedRateLimiter {

    /**
     * 每个窗口只发 <b>1</b> 个许可。这是刻意的：Redisson 是"窗口内先到先得"，配
     * {@code (12, 1秒)} 时 12 个请求可能挤在几十毫秒内打出去；而艺龙按秒限、我方现在配 12
     * 实跑 11.25 就已经每小时几百次频控——突发变粗只会更多。rate 固定为 1、把窗口切到毫秒级，
     * 是用 Redisson 逼近 Guava 那种匀速放行的唯一办法。
     */
    private static final long PERMITS_PER_WINDOW = 1L;

    /** 本地记忆：已写入 Redis 的窗口。命中即跳过读配置，避免热路径多一次往返 */
    private final ConcurrentHashMap<String, Long> appliedWindowMillis = new ConcurrentHashMap<>();

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 取（或建）限流器，只在<b>配额真的变了</b>时才写速率。
     *
     * <p><b>为什么不能每次都 setRate</b>（2026-08-25 真 Redis 实测发现）：Redisson 的
     * {@code setRate} 会<b>重置桶状态</b>。若每次扣格前都调它，等于每次把桶清空——限流完全失效，
     * 任何调用者永远拿得到许可。这比原来的 {@code trySetRate}（改配额不生效）危险得多：
     * 前者是"限不住"，后者只是"改不动"。测试 {@code quotaIsSharedAcrossInstances} 就是被这个
     * 打红的：两个实例各自 setRate，第二个把第一个消耗掉的格子重置回来了。
     *
     * <p>故：本地记住已应用的窗口，命中即直接返回（常态零额外往返）；不命中才读一次 Redis 的
     * 现有配置，<b>只有真的不同</b>才写。这样 Nacos 改配额能生效（第一次不命中时写入），而稳态
     * 下不会反复重置。多实例各有一份本地记忆，配额变更时每个实例最多重置一次。
     *
     * @param qps 每秒许可数，可为小数
     */
    public RRateLimiter getRateLimiter(String name, double qps) {
        long want = windowMillisFor(qps);
        RRateLimiter limiter = redissonClient.getRateLimiter(name);
        Long applied = appliedWindowMillis.get(name);
        if (applied != null && applied == want) {
            return limiter;
        }
        RateLimiterConfig config = limiter.getConfig();
        boolean needWrite = config == null
                || config.getRateInterval() == null || config.getRateInterval() != want
                || config.getRate() == null || config.getRate() != PERMITS_PER_WINDOW;
        if (needWrite) {
            limiter.setRate(RateType.OVERALL, PERMITS_PER_WINDOW, want, RateIntervalUnit.MILLISECONDS);
            log.info("限流配额已写入 Redis: key={}, 每 {}ms 放 {} 个许可（≈{} QPS）",
                    name, want, PERMITS_PER_WINDOW, String.format("%.2f", 1000.0 / want));
        }
        appliedWindowMillis.put(name, want);
        return limiter;
    }

    /** 阻塞取一格：后台用途（刷价、目录摄取），等多久都等 */
    public void acquire(String name, double qps) {
        getRateLimiter(name, qps).acquire();
    }

    /**
     * 等待上限内取一格：前台用途，拿不到就如实失败。
     *
     * @param timeoutMs 等待上限（毫秒）。此前这个参数被丢掉，故超时配置无效
     */
    public boolean tryAcquire(String name, double qps, long timeoutMs) {
        return getRateLimiter(name, qps).tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 把「每秒 N 个」换成「每 X 毫秒 1 个」。Redisson 的 rate 是 {@code long}，装不下小数，
     * 故精度靠窗口承载而不是靠速率。
     *
     * <p>向上取整意味着<b>实际速率略低于配置</b>（配 12 得 1/84ms ≈ 11.9），误差 &lt;1% 且方向偏慢——
     * 对着供应商配额，宁可慢一点也不要超。
     *
     * @return 窗口毫秒数，至少 1
     */
    static long windowMillisFor(double qps) {
        if (qps <= 0) {
            // 配 0 或负数是配置错误。给 1 秒 1 个而不是 0——0 会让 Redisson 永久阻塞，
            // 那种故障形态排查起来比"慢"贵得多
            log.error("[gate] 限流配额非法（qps={}），按 1 QPS 兜底。请检查 ratelimit.qps", qps);
            return 1000L;
        }
        return Math.max(1L, (long) Math.ceil(1000.0 / qps));
    }
}
