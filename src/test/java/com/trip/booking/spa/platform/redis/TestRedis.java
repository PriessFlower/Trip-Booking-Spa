package com.trip.booking.spa.platform.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * 测试用 Redis 端点。默认连本地 {@code tg-local-redis}（127.0.0.1:6380，有口令），
 * 可由 {@code TEST_REDIS_HOST/PORT/PASSWORD} 覆盖——CI 的服务容器不设认证。
 *
 * <p>为什么要这么一层：连真 Redis 的那几条是限流器的守护测试，被测的正是"Redisson 在 Redis 里
 * 怎么算许可"。它们靠 {@code assumeTrue} 在连不上时跳过，于是<b>连接参数写死就等于在 CI 上
 * 静默失效</b>——测试还在，只是不跑。2026-08-25 就是这样：本地全绿 408，CI 上其中 8 条从没执行过。
 */
public final class TestRedis {

    private TestRedis() {
    }

    public static String address() {
        return "redis://" + envOr("TEST_REDIS_HOST", "127.0.0.1")
                + ":" + envOr("TEST_REDIS_PORT", "6380");
    }

    /**
     * 连不上时：本地返回 null（由调用方 {@code assumeTrue} 跳过，没装 Redis 的人不该被挡住），
     * 但 {@code TEST_REDIS_REQUIRED=true} 时<b>直接抛</b>。
     *
     * <p>这个区分是这层的要点。光给 CI 挂上服务容器还不够——容器起不来、端口映射写错、镜像拉不到，
     * 这些都会让连接失败，而 {@code assumeTrue} 会把它们变成"绿的，只是跳过了 8 条"。那正是
     * 2026-08-25 要修的病：<b>守护测试静默失效比没有守护测试更坏</b>，因为它还在报绿。
     * 声明了"这里必须有 Redis"，就该在没有时红。
     */
    public static RedissonClient connectOrNull() {
        String password = envOr("TEST_REDIS_PASSWORD", "local_redis_pw");
        Config config = new Config();
        config.useSingleServer().setAddress(address())
                .setPassword(password.isEmpty() ? null : password)
                .setConnectTimeout(2000).setTimeout(2000);
        try {
            RedissonClient candidate = Redisson.create(config);
            candidate.getBucket("probe:ratelimit").set("1");
            return candidate;
        } catch (Exception e) {
            if (Boolean.parseBoolean(envOr("TEST_REDIS_REQUIRED", "false"))) {
                throw new IllegalStateException("TEST_REDIS_REQUIRED=true 但连不上 " + address()
                        + "：限流器的守护测试无法执行。CI 的 test job 声明了 redis 服务容器，"
                        + "连不上说明容器/端口映射坏了，不能当成「跳过」放行", e);
            }
            return null;
        }
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
