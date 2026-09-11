package com.trip.booking.spa.platform.concurrent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住线程池的"唯一出生地"纪律（PROJECT.md §4.3：形式结构统一，具体分配差异）。
 *
 * <p>旧账即反面：四处各造各的，其中 ThreadPoolUtils 的拒绝策略只打日志不抛出，
 * 队列满时任务静默蒸发；另一个 50 线程池全仓零调用。设施统一后这类账无处再欠。
 */
class ThreadPoolsTest {

    /** §4.3 的强制形态：生产代码里线程池只许出生在 ThreadPools（bff 独立边界除外） */
    @Test
    void threadPoolsAreBornInOnePlaceOnly() throws IOException {
        Path main = Path.of("src/main/java/com/trip/booking/spa");
        try (Stream<Path> files = Files.walk(main)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("platform/concurrent/"))
                    .filter(p -> !p.toString().contains("/bff/"))
                    .filter(p -> {
                        try {
                            String s = Files.readString(p);
                            // 黑名单换白名单：只认「不是 Executors 工厂、不是手搓池」，
                            // 新出的工厂方法一律先失败。此前枚举四个方法名，
                            // Executors.newVirtualThreadPerTaskExecutor 就是这么漏进
                            // SpaController 的（2026-09-10 至 09-11）
                            return s.contains("Executors.new")
                                    || s.contains("new ThreadPoolExecutor(")
                                    || s.contains("new ScheduledThreadPoolExecutor(")
                                    || s.contains("new ForkJoinPool(");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(main::relativize).map(Path::toString)
                    .collect(Collectors.toList());
            assertTrue(offenders.isEmpty(),
                    "线程池只许出生在 ThreadPools（PROJECT.md §4.3），以下文件在自建：" + offenders);
        }
    }

    /** 同名即同池：三个内容摄取服务靠这条共享一个池 */
    @Test
    void sameNameReturnsSamePool() {
        ExecutorService a = ThreadPools.fixedCallerRuns("tp-test-shared", 2, 10);
        ExecutorService b = ThreadPools.fixedCallerRuns("tp-test-shared", 2, 10);
        assertSame(a, b);
        a.shutdown();
    }

    /** 忙则跳过的形状：单线程在跑时第二个任务被拒绝（转译成跳过是调用方的事） */
    @Test
    void serialSkipIfBusyRejectsWhileRunning() throws Exception {
        ExecutorService pool = ThreadPools.serialSkipIfBusy("tp-test-skip", true);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {
        }));
        release.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
    }

    /** 虚拟线程执行器同样只许出生在这里，且同名即同一份（否则每次请求造一个，登记表失去意义） */
    @Test
    void virtualPerTaskIsSharedByName() {
        ExecutorService a = ThreadPools.virtualPerTask("tp-test-virtual");
        ExecutorService b = ThreadPools.virtualPerTask("tp-test-virtual");
        assertSame(a, b);
    }

    /** 在飞数必须进注册表：它是请求路径上并发量最大的一处，漏了等于监控看不见主干 */
    @Test
    void virtualPerTaskReportsInFlight() throws Exception {
        ExecutorService pool = ThreadPools.virtualPerTask("tp-test-virtual-stats");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));

        int[] busy = ThreadPools.stats().get("tp-test-virtual-stats");
        assertEquals(1, busy[0], "活跃=在飞任务数");
        assertEquals(0, busy[2], "无队列，积压恒 0");

        release.countDown();
        for (int i = 0; i < 100 && ThreadPools.stats().get("tp-test-virtual-stats")[0] != 0; i++) {
            Thread.sleep(10);
        }
        int[] idle = ThreadPools.stats().get("tp-test-virtual-stats");
        assertEquals(0, idle[0], "跑完即归零");
        assertEquals(1, idle[3], "已完成计数");
    }

    /** 注册表是监控的挂点：造出来的池必须查得到名字与积压 */
    @Test
    void registryExposesPoolsForMonitoring() {
        ThreadPools.fixed("tp-test-stats", 1, true).shutdown();
        ExecutorService alive = ThreadPools.fixed("tp-test-alive", 1, true);

        assertTrue(ThreadPools.stats().containsKey("tp-test-alive"));
        assertEquals(0, ThreadPools.queueSize("tp-test-alive"));
        assertEquals(0, ThreadPools.queueSize("tp-test-never-created"), "未知池按 0 积压，不抛");
        alive.shutdown();
    }
}
