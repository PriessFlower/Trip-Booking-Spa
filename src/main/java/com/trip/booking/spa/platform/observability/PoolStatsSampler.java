package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.platform.http.HttpUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把线程池与连接池的水位周期性推成 gauge——两处注册表（{@code ThreadPools.stats()}、
 * {@code HttpUtils.poolStats()}）当初就是为「监控只挂一处」留的口子，本类兑现它。
 *
 * <p><b>为什么是采样而不是引用式 gauge</b>：Micrometer 的引用式 gauge 会永久持有对象，
 * 而刷价的池是短命的（每轮建、用完即弃），引用会拖住已终止的池不放；本仓的
 * {@code Monitor.recordValue} 是 set 型 gauge（注册一次、之后只改值），配合采样快照
 * 正好——池换代对指标透明，同名新池接着写同一条曲线。
 *
 * <p><b>消亡的池推一次 0</b>：短命池 shutdown 后从注册表剔除，若不清零，它的水位会
 * 永远冻在最后一个样本上（比如「队列积压 37」），看盘的人会当它还活着。
 *
 * <p>周期 15s，短于 Prometheus 抓取间隔，抓到的最多滞后一个采样周期。
 */
@Component
public class PoolStatsSampler {

    /** 上一轮见过的线程池名，用于给消亡者清零。@Scheduled 单线程调度，无并发 */
    private final Set<String> lastSeenPools = new HashSet<>();

    @Scheduled(fixedDelay = 15_000)
    public void sample() {
        Map<String, int[]> pools = ThreadPools.stats();
        pools.forEach((name, s) -> {
            Monitor.recordValue(MetricNames.THREAD_POOL_ACTIVE, MetricTags.pool(name), s[0]);
            Monitor.recordValue(MetricNames.THREAD_POOL_SIZE, MetricTags.pool(name), s[1]);
            Monitor.recordValue(MetricNames.THREAD_POOL_QUEUE, MetricTags.pool(name), s[2]);
        });
        for (String gone : lastSeenPools) {
            if (!pools.containsKey(gone)) {
                Monitor.recordValue(MetricNames.THREAD_POOL_ACTIVE, MetricTags.pool(gone), 0);
                Monitor.recordValue(MetricNames.THREAD_POOL_SIZE, MetricTags.pool(gone), 0);
                Monitor.recordValue(MetricNames.THREAD_POOL_QUEUE, MetricTags.pool(gone), 0);
            }
        }
        lastSeenPools.clear();
        lastSeenPools.addAll(pools.keySet());

        // 连接池不会消亡（host 池常驻），无需清零那套
        HttpUtils.poolStats().forEach((host, s) -> {
            Monitor.recordValue(MetricNames.HTTP_POOL_LEASED, MetricTags.host(host), s[0]);
            Monitor.recordValue(MetricNames.HTTP_POOL_PENDING, MetricTags.host(host), s[1]);
            Monitor.recordValue(MetricNames.HTTP_POOL_AVAILABLE, MetricTags.host(host), s[2]);
            Monitor.recordValue(MetricNames.HTTP_POOL_MAX, MetricTags.host(host), s[3]);
        });
    }
}
