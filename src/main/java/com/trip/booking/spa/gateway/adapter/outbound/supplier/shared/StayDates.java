package com.trip.booking.spa.gateway.adapter.outbound.supplier.shared;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 住期日期的公共算法（供应商适配层共用）。
 *
 * <p>放在这里而不是各家自己写一份，判据是 PROJECT.md §4.1.3 的首要问句——<b>换一家供应商
 * 要不要改</b>：不要。此前道旅、差旅无忧、美团各有一份逐字相同的私有 {@code nightsOf}。
 */
public final class StayDates {

    private StayDates() {
    }

    /**
     * 住几晚 = 离店日 − 入住日。
     *
     * <p><b>算不出返回 0 而不是抛</b>：调用方拿它与逐晚价条数比对，0 必然对不上，
     * 于是整条报价被丢弃并计数——比抛异常打断整店转换更贴合"一条坏数据只丢一条"。
     */
    public static int nights(String checkIn, String checkOut) {
        try {
            return (int) ChronoUnit.DAYS.between(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
        } catch (Exception e) {
            return 0;
        }
    }
}
