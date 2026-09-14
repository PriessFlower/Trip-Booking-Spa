package com.trip.booking.spa.platform.concurrent;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 每任务一条虚拟线程，且在飞数可查。由 {@link ThreadPools#virtualPerTask} 创建，别处不要 new。
 *
 * <p>只比 {@code Executors.newVirtualThreadPerTaskExecutor()} 多两件事：线程带名字（排障时
 * 栈里认得出是哪条路），以及在飞与已完成计数（进 {@link ThreadPools#stats()}）。
 * 计数是这个类存在的全部理由——不然没法回答"请求路径上现在有多少并发在跑"。
 */
final class VirtualPerTaskExecutor extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong completed = new AtomicLong();

    VirtualPerTaskExecutor(String name) {
        this.delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(name + "-", 1).factory());
    }

    int inFlight() {
        return inFlight.get();
    }

    int completed() {
        return (int) completed.get();
    }

    @Override
    public void execute(Runnable command) {
        inFlight.incrementAndGet();
        try {
            delegate.execute(() -> {
                try {
                    command.run();
                } finally {
                    inFlight.decrementAndGet();
                    completed.incrementAndGet();
                }
            });
        } catch (RuntimeException | Error e) {
            // 提交失败任务不会跑，计数必须回退，否则在飞数只增不减
            inFlight.decrementAndGet();
            throw e;
        }
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
