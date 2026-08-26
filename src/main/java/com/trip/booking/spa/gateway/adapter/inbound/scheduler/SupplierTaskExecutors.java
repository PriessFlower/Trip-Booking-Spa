package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.platform.concurrent.ThreadPools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 供应商独立执行线程（docs/price-refresh.md F-2.7）：每家供应商一条命名单线程，
 * {@code @Scheduled} 调度线程只负责派发、立即返回。
 *
 * <p><b>为什么需要</b>：Spring 默认调度池只有 1 条线程（{@code scheduling-1}），全部
 * 定时任务串行共用——2026-08-18 生产实测，艺龙刷价被 Expedia 轮次挡住，05:00 的触发
 * 排队到 05:07:37 才执行。任务间的等待既不可观测也不受控，一家变慢会拖垮所有家的节奏。
 *
 * <p><b>隔离语义</b>（一份基础设施，按供应商隔离）：
 * <ul>
 *   <li><b>跨供应商并行</b>：elong 卡住不影响 expedia，反之亦然；</li>
 *   <li><b>同供应商串行</b>：同一家的多个任务共享一条线程——同家任务共享同一份
 *       QPS 账号额度，串行本身就是保护，并行只会互抢；</li>
 *   <li><b>忙则跳过，不排队</b>：上一轮未结束时新一轮直接放弃并记日志（cron 下一轮
 *       会再来），不做队列——队列堆积等于把"这家变慢了"这个信号藏起来。</li>
 * </ul>
 *
 * <p><b>与既有防线的关系</b>：Redisson 锁（如 {@code lock:elong:cps:query-price}）防的
 * 是<b>多实例</b>并发，本类防的是<b>本实例内</b>任务互相排队，两者互补而非重复。
 * QPS 额度隔离在限流层（BaseHttpAccess 单闸），与线程无关。
 *
 * <p>线程按供应商命名（{@code elong-task-1}），日志里可直接看出谁在干活。
 */
@Slf4j
@Component
public class SupplierTaskExecutors {

    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 把任务派发到该供应商的专属线程。上一个任务未结束时<b>跳过本次</b>并记日志。
     *
     * @param supplier 供应商名（小写，作线程名前缀）
     * @param taskName 任务名（仅用于日志定位）
     * @param task     任务体；异常在供应商线程内兜底记日志，不外抛
     */
    public void submit(String supplier, String taskName, Runnable task) {
        if (shutdown.get()) {
            log.warn("[supplier-task] 容器正在关闭，拒绝派发 supplier={}, task={}", supplier, taskName);
            return;
        }
        // 池的出生统一走 ThreadPools（PROJECT.md §4.3），本类只持有"忙则跳过"的转译
        ExecutorService executor = executors.computeIfAbsent(supplier,
                s -> ThreadPools.serialSkipIfBusy(s + "-task", false));
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    // 供应商线程是任务的最后一道兜底；吞掉异常防止线程死亡
                    log.error("[supplier-task] 任务异常 supplier={}, task={}", supplier, taskName, e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // SynchronousQueue + 单线程：在跑即拒绝。跳过是信号，不是故障——cron 下一轮会再来
            log.warn("[supplier-task] 上一轮未结束，本轮跳过 supplier={}, task={}", supplier, taskName);
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
        executors.forEach((supplier, executor) -> {
            executor.shutdownNow();
            log.info("[supplier-task] 已关闭 supplier={} 的执行线程", supplier);
        });
    }
}
