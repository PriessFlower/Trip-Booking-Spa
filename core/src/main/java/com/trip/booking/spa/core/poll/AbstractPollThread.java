package com.trip.booking.spa.core.poll;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
@Component
public abstract class AbstractPollThread {

    private long delay = 60; // 延期时间(第一次执行时间-调用时间)
    private long timeInterval = 60; //时间间隔(s)
    private ScheduledExecutorService scheduledExecutor =
        new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("timeout_transaction_checker_thread_"));
    private Timestamp lastProcess = null; //上次执行的时间
    private boolean isTimerStart = false; //定时器是否启动
    private boolean isOnProcessing = false; //是否在执行处理
    private final String threadName; //线程名称
    private final String threadId; //线程标识
    private final String longname; //中文名称
    private long processCount = 0; //执行次数
    private long processTime = 0; //已运行时间
    private long errorProcessCount = 0; //执行失败的次数

    public AbstractPollThread(String threadId, String threadName, String longname, Long delay, Long timeInterval) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.longname = longname;
        if (timeInterval != null && timeInterval > 0) {
            this.timeInterval = timeInterval;
        }

        if (delay != null) {
            this.delay = delay;
        }
        PollThreadManager.get().register(threadName, this);
    }

    /**
     * 线程启动时回调方法。
     */
    public void init() {

    }

    /**
     * 轮询间隔回调方法。
     */
    public abstract void process();

    /**
     * 启动方法
     */
    public void startup() {

        try {

//            this.scheduledExecutor.schedule();

            this.isTimerStart = true;
        } catch (Exception e) {
            this.isTimerStart = false;
            log.error("轮询线程设置定时器失败,", e);
            throw new RuntimeException("轮询线程设置定时器失败", e);
        }
    }

    public void shutdown() {
        try {
            if (this.scheduledExecutor != null) {
                this.scheduledExecutor.shutdown();
            }

            this.isTimerStart = false;
        } catch (Exception e) {
            this.isTimerStart = false;
            log.error("关闭轮询线程中的定时器失败", e);
            throw new RuntimeException("关闭轮询线程中的定时器失败", e);
        }
    }

    public String getThreadName() {
        return this.threadName;
    }

    public String getLongname() {
        return this.longname;
    }

    public long getProcessCount() {
        return this.processCount;
    }

    public long getProcessTime() {
        return this.processTime;
    }

    public long getErrorProcessCount() {
        return this.errorProcessCount;
    }

    public void setTimeInterval(long timeInterval) {
        this.timeInterval = timeInterval;
    }

    public long getTimeInterval() {
        return this.timeInterval;
    }

    public boolean isTimerStart() {
        return this.isTimerStart;
    }

    public boolean isOnProcessing() {
        return this.isOnProcessing;
    }

    public String getLastProcessTime() {
        return this.lastProcess == null ? null : this.lastProcess.toString();
    }

    public void resetCountInfo() {
        this.processCount = 0;
        this.processTime = 0;
        this.errorProcessCount = 0;
        this.lastProcess = null;
    }
}
