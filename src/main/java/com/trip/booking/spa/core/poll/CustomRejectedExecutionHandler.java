package com.trip.booking.spa.core.poll;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        try {
            // 核心改造点，由blockingqueue的offer改成put阻塞方法
            executor.getQueue().put(r);
        } catch (InterruptedException e) {
            log.error("CustomRejectedExecutionHandler is error:{}", e.toString());

        }
    }
}
