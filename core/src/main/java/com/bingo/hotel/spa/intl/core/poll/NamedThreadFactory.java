package com.bingo.hotel.spa.intl.core.poll;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    /**
     * 线程前缀
     */
    private final String prefix;

    /**
     * 线程编号
     */
    private final AtomicInteger threadMumber = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }


    @Override
    public Thread newThread(Runnable r) {
        return new Thread(null, r, prefix + threadMumber.getAndIncrement());
    }
}
