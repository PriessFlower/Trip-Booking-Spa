package com.bingo.hotel.spa.intl.core.redis;

import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import javax.annotation.Resource;
import java.util.Collections;

@Slf4j
public class RedisRateLimiter {
    private String key;
    private int maxPermits;
    private long windowInSeconds;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> redisScript;

    public RedisRateLimiter(String key, int maxPermits, long windowInSeconds) {
        this.key = key;
        this.maxPermits = maxPermits;
        this.windowInSeconds = windowInSeconds;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setResultType(Long.class);
        this.redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate_limiter.lua")));
    }


    public boolean allow() {
        Long count = redisTemplate.execute(redisScript, Collections.singletonList(key),
                String.valueOf(windowInSeconds), String.valueOf(maxPermits));

        if (count != null && count == 0) {
            log.debug("令牌桶={}，获取令牌失败", key);
            throw new RedisLimitException("get global limit false!");
        }

        return count != null && count == 1L;
    }


}
