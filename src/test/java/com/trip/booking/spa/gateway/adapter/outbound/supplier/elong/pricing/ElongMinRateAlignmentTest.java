package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongDataValidateResponse;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉死 {@code H001189} 的自纠正：把逐日 {@code MinRate} 换成艺龙回传的值，<b>{@code Price} 一个字不动</b>。
 *
 * <p><b>为什么必须有</b>：{@code Price} 决定我方向艺龙申报的应付金额，{@code MinRate} 只影响
 * 税费拆分。一旦纠正逻辑动了 {@code Price}，就是在多付钱——2026-08-21 的排查里我方案里真提过
 * "给 Price 加 0.05 元边际"，那是错的（要多付且不可靠）。这条不变量编译期拦不住，只能测。
 *
 * <p>另钉三条：按 {@code Date} 匹配而非数组下标（顺序是艺龙的实现细节，不是契约）；
 * 值没变化时不重试（避免一次白跑的 3.3 秒往返）；日期对不齐时放弃自纠正而不是硬凑。
 */
class ElongMinRateAlignmentTest {

    private static List<ElongDataValidateRequest.DayPrice> align(
            List<ElongDataValidateRequest.DayPrice> dayPrices, String responseJson) throws Exception {
        ElongDataValidateResponse data = JsonUtils.readValue(responseJson, ElongDataValidateResponse.class);
        Method m = ElongPriceServiceImpl.class.getDeclaredMethod(
                "alignMinRateToSupplier", List.class, ElongDataValidateResponse.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ElongDataValidateRequest.DayPrice> out =
                (List<ElongDataValidateRequest.DayPrice>) m.invoke(null, dayPrices, data);
        return out;
    }

    private static ElongDataValidateRequest.DayPrice day(String date, String price, String minRate) {
        return ElongDataValidateRequest.DayPrice.builder()
                .date(date).price(new BigDecimal(price)).minRate(new BigDecimal(minRate)).build();
    }

    /** 只保留自纠正真正读到的层级：Result.interValidateInfo.ratePlanInfo.RateNightlyRateList */
    private static String response(String... nights) {
        return "{\"Code\":\"H001189|每日价传参异常\",\"Result\":{\"ResultCode\":\"Rate\","
                + "\"interValidateInfo\":{\"ratePlanInfo\":{\"RateNightlyRateList\":["
                + String.join(",", nights) + "]}}}}";
    }

    private static String night(String date, String rate, String minRate) {
        return "{\"Date\":\"" + date + "T00:00:00+08:00\",\"Rate\":" + rate + ",\"MinRate\":" + minRate + "}";
    }

    @Test
    @DisplayName("换 MinRate，Price 一个字不动——动了就是多付钱")
    void replacesMinRateAndNeverTouchesPrice() throws Exception {
        List<ElongDataValidateRequest.DayPrice> out = align(
                List.of(day("2026-08-24", "91.79", "82.58")),
                response(night("2026-08-24", "91.79", "82.52")));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getMinRate()).isEqualByComparingTo("82.52");
        assertThat(out.get(0).getPrice()).as("申报价不得被纠正逻辑改动").isEqualByComparingTo("91.79");
        assertThat(out.get(0).getDate()).isEqualTo("2026-08-24");
    }

    @Test
    @DisplayName("多晚按 Date 匹配，不按数组下标——艺龙回传顺序不是契约")
    void matchesByDateNotByIndex() throws Exception {
        List<ElongDataValidateRequest.DayPrice> out = align(
                List.of(day("2026-08-24", "349.99", "304.45"), day("2026-08-25", "259.96", "226.14")),
                // 刻意倒序回传
                response(night("2026-08-25", "259.96", "226.13"), night("2026-08-24", "349.99", "304.44")));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getDate()).isEqualTo("2026-08-24");
        assertThat(out.get(0).getMinRate()).as("倒序回传也要落到对应日期").isEqualByComparingTo("304.44");
        assertThat(out.get(1).getDate()).isEqualTo("2026-08-25");
        assertThat(out.get(1).getMinRate()).isEqualByComparingTo("226.13");
    }

    @Test
    @DisplayName("值完全相同则返回 null——不做一次注定同结果的重试")
    void noRetryWhenNothingChanges() throws Exception {
        assertThat(align(
                List.of(day("2026-08-24", "91.79", "82.52")),
                response(night("2026-08-24", "91.79", "82.52")))).isNull();
    }

    @Test
    @DisplayName("日期对不齐时放弃自纠正，不硬凑")
    void givesUpWhenDatesDoNotLineUp() throws Exception {
        assertThat(align(
                List.of(day("2026-08-24", "91.79", "82.58")),
                response(night("2026-08-26", "91.79", "82.52")))).isNull();
    }

    @Test
    @DisplayName("晚数不符、缺 MinRate、非正数一律放弃")
    void givesUpOnMalformedResponses() throws Exception {
        List<ElongDataValidateRequest.DayPrice> one = List.of(day("2026-08-24", "91.79", "82.58"));
        assertThat(align(one, response(night("2026-08-24", "91.79", "82.52"),
                night("2026-08-25", "91.79", "82.52")))).as("晚数不符").isNull();
        assertThat(align(one, response("{\"Date\":\"2026-08-24T00:00:00+08:00\",\"Rate\":91.79}")))
                .as("缺 MinRate").isNull();
        assertThat(align(one, response(night("2026-08-24", "91.79", "-1")))).as("MinRate 非正").isNull();
        assertThat(align(one, "{\"Code\":\"H001189\",\"Result\":{\"ResultCode\":\"Rate\"}}"))
                .as("响应根本没带逐日价").isNull();
    }
}
