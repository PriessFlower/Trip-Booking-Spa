package com.trip.booking.spa.core.ratelimit;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 静态桥：让 new 出来的 access 对象（非 Spring bean、注入不进去）也能拿到限流中枢。
 * 模式同 {@link com.trip.booking.spa.core.util.SpringAppContextUtil}——启动时把 bean 抄到静态字段。
 */
@Component
public class RateLimitHolder implements ApplicationContextAware {

    private static RateLimitManager manager;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        manager = applicationContext.getBean(RateLimitManager.class);
    }

    public static RateLimitManager get() {
        return manager;
    }
}
