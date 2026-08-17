package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TTL 分档的判据钉死（docs/price-refresh.md F-4）。
 *
 * <p>重点是<b>时区不变性</b>：判定"今天"必须用配置的业务时区，不得用服务器时区。
 * 生产容器跑在 UTC，而 issue #62 已经因为"用服务器时区解释业务日界"付出过代价。
 */
class PriceCacheTtlPolicyTest {

    private static final ZoneId BIZ = ZoneId.of("Asia/Shanghai");

    private PriceCacheTtlPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PriceCacheTtlPolicy();
        ReflectionTestUtils.setField(policy, "zoneId", "Asia/Shanghai");
        ReflectionTestUtils.setField(policy, "futureHours", 24);
        ReflectionTestUtils.setField(policy, "pastHours", 6);
        ReflectionTestUtils.setField(policy, "todayUntilHour", 1);
    }

    @Test
    void futureDatesLiveOneDay() {
        String future = LocalDate.now(BIZ).plusDays(7).toString();
        assertEquals(Duration.ofHours(24).getSeconds(), policy.ttlSeconds(future));
    }

    @Test
    void pastDatesExpireFast() {
        String past = LocalDate.now(BIZ).minusDays(1).toString();
        assertEquals(Duration.ofHours(6).getSeconds(), policy.ttlSeconds(past));
    }

    /** 今天的价延到次日 01:00；结果必落在 (1h, 25h] 之间，且绝不为负 */
    @Test
    void todayLivesUntilTomorrowEarlyMorning() {
        long ttl = policy.ttlSeconds(LocalDate.now(BIZ).toString());
        assertTrue(ttl >= Duration.ofHours(1).getSeconds(), "不得小于下限，否则写入即刻过期");
        assertTrue(ttl <= Duration.ofHours(25).getSeconds(), "最多到次日 01:00");
    }

    /**
     * 核心不变量：同一个日期在任何服务器时区下都得到同档 TTL。
     * 反面教材是 issue #62——用服务器时区解释业务日界，UTC 与东八区差 8 小时。
     */
    @Test
    void resultIsIndependentOfServerTimeZone() {
        String future = LocalDate.now(BIZ).plusDays(3).toString();
        String past = LocalDate.now(BIZ).minusDays(3).toString();
        TimeZone original = TimeZone.getDefault();
        try {
            for (String tz : new String[]{"UTC", "Asia/Shanghai", "America/New_York"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(tz));
                assertEquals(Duration.ofHours(24).getSeconds(), policy.ttlSeconds(future), tz);
                assertEquals(Duration.ofHours(6).getSeconds(), policy.ttlSeconds(past), tz);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /** 解析不出的日期按未来档（偏长而非偏短）：TTL 判定失误不该让刚刷的价立刻消失 */
    @Test
    void unparsableDateFallsBackToFutureTier() {
        assertEquals(Duration.ofHours(24).getSeconds(), policy.ttlSeconds("not-a-date"));
        assertEquals(Duration.ofHours(24).getSeconds(), policy.ttlSeconds(null));
    }

    /** 时区配错不该让刷价失败，回落业务默认值 */
    @Test
    void invalidZoneFallsBackInsteadOfThrowing() {
        ReflectionTestUtils.setField(policy, "zoneId", "Not/AZone");
        String future = LocalDate.now(BIZ).plusDays(2).toString();
        assertEquals(Duration.ofHours(24).getSeconds(), policy.ttlSeconds(future));
    }
}
