package com.trip.booking.spa.core.redis;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DistributedRateLimiter {

    @Autowired
    private RedissonClient redissonClient;

    public RRateLimiter getRateLimiter(String name, long rate, RateIntervalUnit unit, long rateInterval) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(name);
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, unit);
        return rateLimiter;
    }

    public boolean tryAcquire(String name, long rate, RateIntervalUnit unit, long rateInterval, int timeOut) {
        RRateLimiter rateLimiter = getRateLimiter(name, rate, unit, rateInterval);
        long l = rateLimiter.availablePermits();
        return rateLimiter.tryAcquire(1);
    }
}
