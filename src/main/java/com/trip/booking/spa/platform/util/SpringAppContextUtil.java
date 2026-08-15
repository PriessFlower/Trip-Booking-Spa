package com.trip.booking.spa.platform.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 获得spring 上下文
 *
 * @author: zhe.hao
 */

@Component
public class SpringAppContextUtil implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AppContext.setApplicationContextHolder(applicationContext);
    }

    public static class AppContext {
        private static ApplicationContext applicationContextHolder;

        public static void setApplicationContextHolder(ApplicationContext context) {
            applicationContextHolder = context;
        }

        public static ApplicationContext getApplicationContextHolder() {
            return applicationContextHolder;
        }

        public static <T> T getBean(Class<T> t) {
            return applicationContextHolder.getBean(t);

        }

        public static <T> T getBean(Class<T> clazz, String beanName) {
            return applicationContextHolder.getBean(beanName, clazz);
        }

        public static <T> Map<String, T> getBeanOfType(Class<T> clazz) {
            return applicationContextHolder.getBeansOfType(clazz);
        }

        public static <T> T getBean(String beanName) {
            return (T) applicationContextHolder.getBean(beanName);
        }

        public static <T> T getInstance(Class<T> tClass, T handle) {
            if (null == handle) {
                handle = getBean(tClass);
            }
            return handle;
        }
    }
}
