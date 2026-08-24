package com.trip.booking.spa.platform.ratelimit;

import com.trip.booking.spa.platform.exception.RedisLimitException;

/**
 * 取供应商调用许可的<b>唯一入口</b>。两级桶的规则只在这里实现一份：
 *
 * <pre>
 *   GLOBAL_LIMIT:&lt;供应商&gt;:&lt;接口&gt;            接口桶＝对供应商的承诺，硬顶，必扣
 *   GLOBAL_LIMIT:&lt;供应商&gt;:&lt;接口&gt;:&lt;用途&gt;     用途桶＝我方内部怎么分，登记了才扣
 * </pre>
 *
 * <p><b>为什么要收成一处</b>：取许可的地方原先有四处各写一遍——通道层
 * （{@code BaseHttpAccess}）、大文件下载（{@code ChunkedFileAccess}）、Expedia 的两个静态
 * 内容客户端（它们直接用 RestTemplate，不经通道层）。四处只有第一处后来加了用途桶，于是
 * "每条路都受分配约束"这个前提在另外三处是不成立的——而分配一旦有例外就退化成建议值。
 *
 * <p>用途桶<b>未登记时整格跳过</b>，只扣接口桶：没登记就等于不分配。不能拿 qps 取值判断，
 * 未登记会回落 {@code default-qps}，那会把"忘配子桶"变成"子桶有个很大的额度"。这也让代码
 * 可以先于配置发布——新键还没进 Nacos 时行为与改动前完全一致。
 */
public final class Permits {

    private Permits() {
    }

    /**
     * 扣「用途桶（若已登记）+ 接口桶」各一格。先扣较紧的用途桶，再扣接口桶。
     *
     * @throws RedisLimitException 前台用途在等待上限内拿不到许可（后台用途只会等，不会抛）
     */
    public static void take(String interfaceKey, CallPurpose purpose) {
        RateLimitManager manager = RateLimitHolder.get();
        String purposeKey = interfaceKey + ":" + purpose.name();
        if (manager.isRegistered(purposeKey)) {
            takeOne(manager, purposeKey, purpose);
        }
        takeOne(manager, interfaceKey, purpose);
    }

    /**
     * 等还是走由用途定，不由调用点定。此前是"刷价在业务代码里阻塞、其余在通道层非阻塞"，
     * 同一件事分散两层，读代码的人得两处都读到才知道自己会不会被挂住。
     */
    private static void takeOne(RateLimitManager manager, String key, CallPurpose purpose) {
        if (!purpose.failFast()) {
            // 后台：被限流挡掉会计入失败态而不动缓存（F-5.1），等于凭空造一次假失败。
            // 对没人等的调用，等待永远优于失败
            manager.acquire(key);
            return;
        }
        if (!manager.tryAcquire(key)) {
            throw new RedisLimitException("Request exceeds rate limit, key = " + key);
        }
    }
}
