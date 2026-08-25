package com.trip.booking.spa.platform.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 用<b>真 Redis</b> 验跨实例限流器的三条语义。不用 mock：被测的正是"Redisson 在 Redis 里怎么
 * 算许可"，把 Redis 换成假的等于什么都没验。
 *
 * <p>连本地 {@code tg-local-redis}（127.0.0.1:6380）。连不上就跳过——CI 上没有 Redis 时不该红。
 *
 * <p>三条语义各对应一个已修的缺陷：改速率要生效（原 {@code trySetRate} 是空操作）、
 * 小数配额不能被截成 0（原 {@code (long) qps}）、超时参数要真的用上（原来被丢掉）。
 */
class DistributedRateLimiterTest {

    private static final String HOST = "redis://127.0.0.1:6380";
    private static final String PASSWORD = "local_redis_pw";

    private static RedissonClient client;
    private static DistributedRateLimiter limiter;

    @BeforeAll
    static void connect() {
        Config config = new Config();
        config.useSingleServer().setAddress(HOST).setPassword(PASSWORD)
                .setConnectTimeout(2000).setTimeout(2000);
        RedissonClient candidate = null;
        try {
            candidate = Redisson.create(config);
            candidate.getBucket("probe:ratelimit").set("1");
        } catch (Exception e) {
            candidate = null;
        }
        assumeTrue(candidate != null, "本地 Redis(6380) 不可用，跳过");
        client = candidate;
        limiter = new DistributedRateLimiter();
        ReflectionTestUtils.setField(limiter, "redissonClient", client);
    }

    @AfterAll
    static void close() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("改配额必须生效——trySetRate 是空操作，热改会静默失效")
    void rateChangeTakesEffect() {
        String key = "test:ratelimit:change:" + System.nanoTime();
        limiter.getRateLimiter(key, 1.0);
        RRateLimiter rl = client.getRateLimiter(key);
        long firstWindow = rl.getConfig().getRateInterval();

        // 同一个键改成 10 QPS：窗口应从 1000ms 变成 100ms
        limiter.getRateLimiter(key, 10.0);
        long secondWindow = client.getRateLimiter(key).getConfig().getRateInterval();

        assertEquals(1000L, firstWindow, "1 QPS 应换成每 1000ms 一个许可");
        assertEquals(100L, secondWindow,
                "改配额没生效（窗口仍是 " + secondWindow + "ms）。trySetRate 在已配置时是空操作，"
                        + "于是 Nacos 改了 qps 而限流器冻在第一次的值上");
        rl.delete();
    }

    @Test
    @DisplayName("小数配额不能被截成 0——0.5 QPS 要变成每 2 秒 1 个，而不是永久阻塞")
    void fractionalQpsSurvives() {
        assertEquals(2000L, DistributedRateLimiter.windowMillisFor(0.5));
        assertEquals(84L, DistributedRateLimiter.windowMillisFor(12.0),
                "12 QPS 应换成每 84ms 一个（向上取整＝实际略慢于配置，对着供应商配额宁可慢）");
        assertEquals(1L, DistributedRateLimiter.windowMillisFor(5000.0), "极高配额时窗口下限 1ms");
        assertEquals(1000L, DistributedRateLimiter.windowMillisFor(0),
                "配 0 是配置错误，兜底 1 QPS 而不是 0——0 会让 Redisson 永久阻塞，"
                        + "那种故障形态排查起来比慢贵得多");
    }

    @Test
    @DisplayName("额度耗尽时 tryAcquire 必须真的等——超时参数此前被丢掉")
    void tryAcquireHonoursTimeout() {
        String key = "test:ratelimit:timeout:" + System.nanoTime();
        // 每 500ms 一个许可
        assertTrue(limiter.tryAcquire(key, 2.0, 50), "第一格应立刻拿到");

        long start = System.currentTimeMillis();
        boolean got = limiter.tryAcquire(key, 2.0, 900);
        long waited = System.currentTimeMillis() - start;

        assertTrue(got, "给了 900ms 上限、而补一格只要 500ms，应当等到");
        assertTrue(waited >= 200,
                "只等了 " + waited + "ms 就返回——超时参数没被用上，等于 acquire-timeout-ms 形同虚设");

        // 上限小于补格时间则如实失败
        long start2 = System.currentTimeMillis();
        assertFalse(limiter.tryAcquire(key, 2.0, 50), "50ms 上限内补不上一格，应如实返回 false");
        assertTrue(System.currentTimeMillis() - start2 < 400, "应快速失败，不该傻等");
        client.getRateLimiter(key).delete();
    }

    @Test
    @DisplayName("配额跨「实例」共享——这是换掉 Guava 的全部理由")
    void quotaIsSharedAcrossInstances() {
        String key = "test:ratelimit:shared:" + System.nanoTime();
        // 两个 DistributedRateLimiter 实例模拟两台机器，共用同一个 Redis 键
        DistributedRateLimiter other = new DistributedRateLimiter();
        ReflectionTestUtils.setField(other, "redissonClient", client);

        assertTrue(limiter.tryAcquire(key, 1.0, 50), "实例 A 拿到唯一那一格");
        assertFalse(other.tryAcquire(key, 1.0, 50),
                "实例 B 也拿到了许可——配额没有跨实例共享。Guava 就是这个行为："
                        + "每个 JVM 各算一份，两台机器对供应商就是双倍流量");
        client.getRateLimiter(key).delete();
    }
}
