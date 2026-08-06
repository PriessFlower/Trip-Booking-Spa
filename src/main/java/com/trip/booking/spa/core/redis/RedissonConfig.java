package com.trip.booking.spa.core.redis;

import org.springframework.beans.factory.annotation.Value;
import org.redisson.Redisson;
import org.redisson.config.SingleServerConfig;
import org.redisson.config.Config;
import org.redisson.api.RedissonClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${spring.redis.lettuce.pool.max-idle}")
    private int maxIdle;

    @Value("${spring.redis.lettuce.pool.min-idle}")
    private int minIdle;

    @Value("${spring.redis.lettuce.pool.max-active}")
    private int maxActive;

    // 注意：没有minIdle，因为Redisson没有对应的配置

    @Bean
    public RedissonClient redisson() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(minIdle) // 设置最小空闲连接数
                .setConnectionPoolSize(maxActive);// 设置最大连接数
        if (StringUtils.isNotBlank(password)) {
            singleServerConfig.setPassword(password);
        }
        return Redisson.create(config);
    }

}
