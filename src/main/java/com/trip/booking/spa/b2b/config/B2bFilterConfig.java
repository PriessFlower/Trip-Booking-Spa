package com.trip.booking.spa.b2b.config;

import com.trip.booking.spa.b2b.auth.B2bAuthFilter;
import com.trip.booking.spa.b2b.auth.SessionStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 登录闸门的唯一注册点，路径写死为 {@code /b2b/*}。
 *
 * <p>{@link B2bAuthFilter} 不做成 {@code @Component} 就是为了让这里成为唯一注册点——
 * 自动注册会落到 {@code /*}，把上游取货与监控端点一起挡掉。改这里的 URL 模式等于
 * 改鉴权范围，应当与本包的端点前缀一同评审。
 */
@Configuration
public class B2bFilterConfig {

    @Bean
    public FilterRegistrationBean<B2bAuthFilter> b2bAuthFilterRegistration(SessionStore sessionStore) {
        FilterRegistrationBean<B2bAuthFilter> registration =
                new FilterRegistrationBean<>(new B2bAuthFilter(sessionStore));
        registration.addUrlPatterns("/b2b/*");
        registration.setName("b2bAuthFilter");
        return registration;
    }
}
