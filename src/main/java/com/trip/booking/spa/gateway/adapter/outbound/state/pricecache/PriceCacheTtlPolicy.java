package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 价格缓存的 TTL 分档（docs/price-refresh.md F-4）：按入住日期的远近给不同存活期。
 *
 * <p><b>TTL 不只是省内存，它是刷价失败时的兜底降级手段</b>（F-4.3）。刷不动的时候
 * 缓存自然过期 → 上游查不到 → 如实回报无价，而不是拿一份越来越陈的价对外报
 * （R-1.6 宁可少卖，不可卖错）。此前一律 1 天，等于"昨天的价"和"下个月的价"
 * 用同一个失效速度——前者早已无意义却还占着位，后者刚刷完就可能提前失效。
 *
 * <p>分档（承 hotel-spa，本仓前身的实测设计）：
 * <table>
 *   <tr><th>入住日期</th><th>TTL</th><th>理由</th></tr>
 *   <tr><td>今天</td><td>到次日 01:00</td><td>当日价过零点即失去意义，留一小时给跨零点的收尾流量</td></tr>
 *   <tr><td>未来</td><td>1 天</td><td>一个刷价周期内必被覆盖</td></tr>
 *   <tr><td>已过期</td><td>6 小时</td><td>仅供对账追溯，尽快让位</td></tr>
 * </table>
 *
 * <p><b>时区必须显式指定，不得用服务器时区。</b>生产容器跑在 UTC（2026-08-17 实测），
 * 而"今天"是酒店当地的概念——用服务器时区判定会让同一份价在不同部署环境下得到
 * 不同的存活期。这与 issue #62（退改截止时间随服务器时区漂移 8 小时）是同一类
 * 错误，那次的代价是把免费取消窗口说长、旅客据此在窗口外取消挨罚金。
 */
@Slf4j
@Component
@RefreshScope
public class PriceCacheTtlPolicy {

    /**
     * 判定"今天/未来/过期"所用的时区。
     *
     * <p>默认 Asia/Shanghai：本仓当前的供应商（艺龙国内与东南亚、Expedia 面向中国渠道）
     * 与上游渠道均以北京时间为业务日界。<b>不取服务器时区</b>——理由见类注释。
     *
     * <p>本值只影响 TTL 的档位判定，不影响价格本身；即便跨时区酒店的日界与此有偏差，
     * 后果也只是 TTL 早一档或晚一档，不会让价格出错。
     */
    @Value("${cache.price.ttl-zone:Asia/Shanghai}")
    private String zoneId;

    /** 未来日期的存活期（小时）。须 ≥ 一个刷价周期，否则刷回来之前就先没了 */
    @Value("${cache.price.ttl-future-hours:24}")
    private int futureHours;

    /** 已过期日期的存活期（小时）。只为对账追溯保留 */
    @Value("${cache.price.ttl-past-hours:6}")
    private int pastHours;

    /** 今天的价延到次日几点。留出跨零点的收尾流量窗口 */
    @Value("${cache.price.ttl-today-until-hour:1}")
    private int todayUntilHour;

    /**
     * 按入住日期算存活秒数。
     *
     * @param date 入住日期 {@code yyyy-MM-dd}；解析不出时回落未来档（偏长而非偏短——
     *             宁可多留一会儿，也不要因为解析失败让刚刷的价立刻消失）
     */
    public long ttlSeconds(String date) {
        ZoneId zone = resolveZone();
        LocalDate today = LocalDate.now(zone);
        LocalDate target;
        try {
            target = LocalDate.parse(date);
        } catch (Exception e) {
            log.warn("价格缓存 TTL：日期无法解析，按未来档处理,date={}", date);
            return Duration.ofHours(futureHours).getSeconds();
        }
        if (target.isBefore(today)) {
            return Duration.ofHours(pastHours).getSeconds();
        }
        if (target.isEqual(today)) {
            ZonedDateTime deadline = today.plusDays(1).atTime(LocalTime.of(todayUntilHour, 0)).atZone(zone);
            long seconds = Duration.between(ZonedDateTime.now(zone), deadline).getSeconds();
            // 已过 01:00 时该值为负；给一个下限，避免写入即刻过期
            return Math.max(seconds, Duration.ofHours(1).getSeconds());
        }
        return Duration.ofHours(futureHours).getSeconds();
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(zoneId);
        } catch (Exception e) {
            // 配错时区不该让刷价整体失败；退回业务默认值并告警
            log.warn("价格缓存 TTL：时区配置非法，回落 Asia/Shanghai,配置值={}", zoneId);
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
