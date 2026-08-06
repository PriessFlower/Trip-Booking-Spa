package com.trip.booking.spa.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mybatisPlus配置类.
 *
 * @author : hanJH
 * @version : 1.0 2024/03/05
 * @since : 1.0
 **/
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); //配置插件类
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); //具体到配置哪一个插件
        return interceptor;
    }
}

