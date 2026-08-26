package com.trip.booking.spa.platform.concurrent;

import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池的唯一出生地：<b>形式结构统一，具体分配差异</b>（PROJECT.md §4.3）。
 *
 * <p>与限流器同一哲学——设施一份（Redisson 令牌桶只有一种实现），差异全在分配
 * （每个桶自己的 QPS）。线程池此前是反面：四处各造各的（Expedia 静态 ThreadPoolUtils、
 * 艺龙内联回写池、刷价每轮自建、下载每次自建），形状各异、无处统一观测，其中
 * ThreadPoolUtils 的拒绝策略只打日志不抛出——<b>队列满时任务被静默扔掉</b>。
 *
 * <p>本类只统一「创建与登记」，<b>不统一语义</b>：忙则跳过（调度）、满则弃（回写）、
 * 定容批处理（刷价/下载）是三种刻意不同的分配，由调用方按形状挑选、按名登记。
 * 所有池进 {@link #stats()} 注册表——池叫什么、几线程、积压多少，一处可查，
 * 监控接指标时只挂这一处。
 *
 * <p>短命池（刷价每轮、单次下载）用完由调用方 shutdown，注册表在下次读取时
 * 自动剔除已终止者；同名重建视为换代，直接顶替。
 */
public final class ThreadPools {

    private static final ConcurrentHashMap<String, ThreadPoolExecutor> REGISTRY = new ConcurrentHashMap<>();

    private ThreadPools() {
    }

    /**
     * 单线程 + 零容量队列：在跑即拒绝（抛 {@link java.util.concurrent.RejectedExecutionException}）。
     * 供"忙则跳过"语义用——跳过的转译（记日志、放弃本轮）留在调用方，见 SupplierTaskExecutors。
     */
    public static ExecutorService serialSkipIfBusy(String name, boolean daemon) {
        return register(name, new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), namedFactory(name, daemon), countingAbort(name)));
    }

    /**
     * 单线程 + 有界队列：满则拒绝（抛出，调用方自决是丢弃还是告警）。
     * 供"尽力而为的旁路"用（如验价即刷回写：宁可丢一次，不许积压拖住主流程）。
     */
    public static ExecutorService serialBounded(String name, int queueCapacity, boolean daemon) {
        return register(name, new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), namedFactory(name, daemon), countingAbort(name)));
    }

    /** 定容池，无界队列：短命批处理用（刷价每轮、单次下载），用完调用方 shutdown */
    public static ExecutorService fixed(String name, int threads, boolean daemon) {
        return register(name, new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), namedFactory(name, daemon), countingAbort(name)));
    }

    /**
     * 定容池 + 有界队列 + <b>打回调用者线程执行</b>的背压：常驻批处理用（静态内容摄取）。
     *
     * <p>取代旧 ThreadPoolUtils 的两件次品：自旋 sleep 等队列（递归睡 1 秒）与
     * 只打日志的拒绝策略（任务静默蒸发）。CallerRuns 让生产者在池满时自己干活，
     * 天然限速且一个任务都不丢。
     */
    public static ExecutorService fixedCallerRuns(String name, int threads, int queueCapacity) {
        return REGISTRY.compute(name, (n, existing) -> {
            if (existing != null && !existing.isShutdown()) {
                return existing;
            }
            return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity), namedFactory(name, false),
                    countingCallerRuns(name));
        });
    }

    /**
     * 语义同缺省 AbortPolicy（照抛 {@link RejectedExecutionException}），只是抛前计一笔
     * {@code thread_pool_rejected}。拒绝是调用方语义的一部分（忙则跳过、满则弃），
     * 但「弃了多少」必须可见——静默的拒绝和被吞的任务只差一个 catch。
     */
    private static RejectedExecutionHandler countingAbort(String name) {
        return (r, pool) -> {
            Monitor.recordOne(MetricNames.THREAD_POOL_REJECTED, MetricTags.pool(name));
            throw new RejectedExecutionException("任务被线程池 " + name + " 拒绝（队列满或已关闭）");
        };
    }

    /** 语义同 CallerRunsPolicy，触发时计一笔 {@code thread_pool_caller_runs}：摄取在变慢的直接信号 */
    private static RejectedExecutionHandler countingCallerRuns(String name) {
        ThreadPoolExecutor.CallerRunsPolicy delegate = new ThreadPoolExecutor.CallerRunsPolicy();
        return (r, pool) -> {
            Monitor.recordOne(MetricNames.THREAD_POOL_CALLER_RUNS, MetricTags.pool(name));
            delegate.rejectedExecution(r, pool);
        };
    }

    /** 某池当前积压（注册表按名查）；无此池返回 0。静态内容摄取用它做批间限速 */
    public static int queueSize(String name) {
        ThreadPoolExecutor pool = REGISTRY.get(name);
        return pool == null ? 0 : pool.getQueue().size();
    }

    /** 注册表快照：池名 → [活跃线程, 池大小, 队列积压, 已完成]。监控接指标只挂这一处 */
    public static Map<String, int[]> stats() {
        prune();
        Map<String, int[]> snapshot = new LinkedHashMap<>();
        REGISTRY.forEach((name, pool) -> snapshot.put(name, new int[]{
                pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size(),
                (int) pool.getCompletedTaskCount()}));
        return snapshot;
    }

    private static ExecutorService register(String name, ThreadPoolExecutor pool) {
        prune();
        REGISTRY.put(name, pool);
        return pool;
    }

    private static void prune() {
        REGISTRY.entrySet().removeIf(e -> e.getValue().isTerminated());
    }

    private static ThreadFactory namedFactory(String name, boolean daemon) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + seq.incrementAndGet());
            t.setDaemon(daemon);
            return t;
        };
    }
}
