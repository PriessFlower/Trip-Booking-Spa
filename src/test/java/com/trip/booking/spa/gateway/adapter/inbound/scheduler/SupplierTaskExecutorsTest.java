package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供应商独立执行线程的语义钉死（docs/price-refresh.md F-2.7）。
 *
 * <p>背景：Spring 默认调度池单线程，全部定时任务串行——2026-08-18 生产实测艺龙刷价
 * 被 Expedia 轮次排队 7 分半。本类保证四条语义：跨供应商并行、同供应商忙则跳过、
 * 任务异常不杀线程、线程按供应商命名。
 */
class SupplierTaskExecutorsTest {

    private SupplierTaskExecutors executors;

    @BeforeEach
    void setUp() {
        executors = new SupplierTaskExecutors();
    }

    @AfterEach
    void tearDown() {
        executors.shutdown();
    }

    /** 核心命题：一家卡住不影响另一家——这正是生产里 elong 被 expedia 排队的反面 */
    @Test
    void suppliersRunInParallel() throws Exception {
        CountDownLatch elongBlocking = new CountDownLatch(1);
        CountDownLatch expediaDone = new CountDownLatch(1);

        executors.submit("elong", "t", () -> await(elongBlocking));
        executors.submit("expedia", "t", expediaDone::countDown);

        assertTrue(expediaDone.await(2, TimeUnit.SECONDS),
                "elong 阻塞期间 expedia 必须照常执行");
        elongBlocking.countDown();
    }

    /** 同供应商忙则跳过：慢是信号，不许静默堆积 */
    @Test
    void busySupplierSkipsInsteadOfQueueing() throws Exception {
        CountDownLatch blocking = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();

        executors.submit("elong", "round-1", () -> {
            executed.incrementAndGet();
            await(blocking);
        });
        waitUntil(() -> executed.get() == 1);
        // 第一轮还在跑，第二轮应被跳过而非排队
        executors.submit("elong", "round-2", executed::incrementAndGet);
        blocking.countDown();

        Thread.sleep(200);
        assertEquals(1, executed.get(), "上一轮未结束时新一轮必须跳过");
    }

    /** 异常兜底在供应商线程内：一轮炸了，下一轮照常 */
    @Test
    void exceptionDoesNotKillTheLane() throws Exception {
        CountDownLatch secondDone = new CountDownLatch(1);

        executors.submit("elong", "boom", () -> {
            throw new IllegalStateException("模拟任务异常");
        });
        waitUntil(() -> {
            executors.submit("elong", "next", secondDone::countDown);
            return secondDone.getCount() == 0;
        });
        assertTrue(secondDone.await(2, TimeUnit.SECONDS), "异常后该供应商线程必须还活着");
    }

    /** 线程按供应商命名，日志里能直接看出谁在干活 */
    @Test
    void threadsAreNamedBySupplier() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        executors.submit("elong", "t", () -> {
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("elong-task-1", threadName.get());
    }

    /** 关闭后拒绝新任务，不再拉起线程 */
    @Test
    void rejectsAfterShutdown() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        executors.shutdown();
        executors.submit("elong", "late", executed::incrementAndGet);
        Thread.sleep(100);
        assertEquals(0, executed.get(), "shutdown 后不得再执行任务");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 轮询等待条件成立（上限 2s），避免用固定 sleep 赌时序 */
    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("2s 内条件未成立");
            }
            Thread.sleep(20);
        }
    }
}
