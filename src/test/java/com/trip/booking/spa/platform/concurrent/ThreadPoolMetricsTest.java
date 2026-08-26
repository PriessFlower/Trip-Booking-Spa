package com.trip.booking.spa.platform.concurrent;

import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拒绝与 CallerRuns 背压必须可数（thread_pool_rejected / thread_pool_caller_runs）。
 *
 * <p>拒绝是分配语义的一部分（忙则跳过、满则弃），但「弃了多少」此前只有调用方
 * 各自的日志——静默的拒绝和被吞的任务只差一个 catch。CallerRuns 触发即摄取在
 * 变慢，不打指标就只能靠感觉发现。语义本身不许变：该抛照抛、该打回照打回。
 */
class ThreadPoolMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        // Monitor 的服务是静态注入，不还原会让其他测试悄悄开始计数
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("忙则跳过：拒绝照抛，且计一笔 thread_pool_rejected{pool}")
    void rejectionIsCountedAndStillThrows() throws Exception {
        ExecutorService pool = ThreadPools.serialSkipIfBusy("tpm-rej", true);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                running.countDown();
                await(release);
            });
            assertTrue(running.await(2, TimeUnit.SECONDS));

            assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {
            }));

            assertEquals(1.0, registry.counter("thread_pool_rejected_count",
                    "pool", "tpm-rej").count());
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("CallerRuns：任务打回提交者执行，且计一笔 thread_pool_caller_runs{pool}")
    void callerRunsIsCountedAndStillRuns() throws Exception {
        ExecutorService pool = ThreadPools.fixedCallerRuns("tpm-cr", 1, 1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                running.countDown();
                await(release);
            });
            assertTrue(running.await(2, TimeUnit.SECONDS));
            pool.execute(() -> {
            });

            // 池与队列均满：这次提交应在当前线程执行完成（而非丢弃或抛出）
            boolean[] ranInCaller = {false};
            String caller = Thread.currentThread().getName();
            pool.execute(() -> ranInCaller[0] = Thread.currentThread().getName().equals(caller));

            assertTrue(ranInCaller[0]);
            assertEquals(1.0, registry.counter("thread_pool_caller_runs_count",
                    "pool", "tpm-cr").count());
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
