package com.bingo.hotel.spa.intl.core.util;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.TimeUnit;

public class RateLimiterUtils {
    public static RateLimiter LIMITER = RateLimiter.create(20);
    public static RateLimiter LIMITEROne = RateLimiter.create(20);
    public static RateLimiter LIMITERTwo = RateLimiter.create(20);
    public static RateLimiter LIMITERTree = RateLimiter.create(20);
    public static RateLimiter LIMITERFour = RateLimiter.create(5);
    public static RateLimiter LIMITERFive = RateLimiter.create(5);
    public static RateLimiter LIMITERSix = RateLimiter.create(5);
    public static RateLimiter LIMITERSeven = RateLimiter.create(5);
    public static RateLimiter LIMITEREight = RateLimiter.create(5);
    public static RateLimiter LIMITERNine = RateLimiter.create(5);
    public static RateLimiter LIMITERNTen = RateLimiter.create(5);


}
