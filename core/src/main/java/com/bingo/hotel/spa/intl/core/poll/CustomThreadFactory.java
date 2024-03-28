package com.bingo.hotel.spa.intl.core.poll;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {

    private String name;
    private AtomicInteger count = new AtomicInteger(0);

    public CustomThreadFactory(String name){
        this.name = name;
    }
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        String threadName = name + count.addAndGet(1);
        t.setName(threadName);
        return t;
    }
}
