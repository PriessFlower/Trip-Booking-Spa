package com.bingo.hotel.spa.intl.core.api.expedia.utils;


import com.bingo.hotel.spa.intl.core.poll.CustomRejectedExecutionHandler;
import com.bingo.hotel.spa.intl.core.poll.CustomThreadFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 自定义线程创建工具类，创建线程池后不需要关闭
 *
 * @author liangxn
 */
@Log4j2
public class ThreadPoolUtils {

    private static final ExecutorService POOL = new ThreadPoolExecutor(0, 50, 60L
            , TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            new CustomThreadFactory("expedia_query_regions"),
            new CustomRejectedExecutionHandler());


    public static ExecutorService getPool(){
        return POOL;
    }

    private static ThreadPoolExecutor threadPool = null;
    private static final String POOL_NAME = "myPool";
    // 等待队列长度
    private static final int BLOCKING_QUEUE_LENGTH = 1000;
    // 闲置线程存活时间
    private static final int KEEP_ALIVE_TIME = 60 * 1000;

    private ThreadPoolUtils() {
        throw new IllegalStateException("utility class");
    }


    /**
     * 无返回值直接执行
     *
     * @param runnable 需要运行的任务
     */
    public static void execute(Runnable runnable) {
        ThreadPoolExecutor executor = getThreadPool();
        executor.execute(runnable);
        if(executor.getQueue().size()>200){
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("任务提交至线程池。当前线程池队列大小: {}", executor.getQueue().size());
    }

    /**
     * 有返回值执行
     * 主线程中使用Future.get()获取返回值时，会阻塞主线程，直到任务执行完毕
     *
     * @param callable 需要运行的任务
     */
    public static <T> Future<T> submit(Callable<T> callable) {
        return getThreadPool().submit(callable);
    }

    private static synchronized ThreadPoolExecutor getThreadPool() {
        if (threadPool == null) {
            // 获取处理器数量
//            int cpuNum = Runtime.getRuntime().availableProcessors();
            // 根据cpu数量,计算出合理的线程并发数
            int maximumPoolSize = 10;
            // 核心线程数、最大线程数、闲置线程存活时间、时间单位、线程队列、线程工厂、当前线程数已经超过最大线程数时的异常处理策略
            threadPool = new ThreadPoolExecutor(maximumPoolSize - 1,
                    maximumPoolSize,
                    KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingDeque<>(BLOCKING_QUEUE_LENGTH),
                    new ThreadFactoryBuilder().setNameFormat(POOL_NAME + "-%d").build(),
                    new ThreadPoolExecutor.AbortPolicy() {
                        @Override
                        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                            log.warn("线程爆炸了，当前运行线程总数：{}，活动线程数：{}。等待队列已满，等待运行任务数：{}",
                                    e.getPoolSize(),
                                    e.getActiveCount(),
                                    e.getQueue().size());
                        }
                    });

        }
        return threadPool;
    }

    /**
     * 将一个大集合分成几份等量
     * @param list 集合
     * @param numberOfParts  几份
     * @return
     * @param <T>
     */
    public static <T> List<List<T>> splitListIntoParts(List<T> list, int numberOfParts) {
        final int size = list.size();
        final int chunkSize = size / numberOfParts;
        final int leftOver = size % numberOfParts;
        List<List<T>> parts = new ArrayList<>(numberOfParts);
        int start = 0;

        for (int i = 0; i < numberOfParts; i++) {
            int end = start + chunkSize + (i < leftOver ? 1 : 0);
            parts.add(new ArrayList<>(list.subList(start, end)));
            start = end;
        }

        return parts;
    }
}