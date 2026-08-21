package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉死每日价取的是 <b>Rate</b>（含税结算口径），不是 Member（会员价）。
 *
 * <p><b>为什么必须有这个测试</b>：这一个字段决定我方付给艺龙多少钱。取错成 Member 时，
 * 申报值比结算价高 1.4%~10%（837 报价实测，中位 8.3%），而结算按申报值走（2026-07 月结单
 * 订单 101000106416「艺龙卖价/分销商卖价/结算金额」三列均等于申报值）——即每单多付，
 * 且全链路无任何一处会报错：验价通过、下单成功、对账单也"对得上"。
 *
 * <p>口径依据：艺龙【国际酒店】国际对接指南（faq id=337）「①价格取Rate；②税费=Rate-MinRate」，
 * 2026-08-21 艺龙对接人微信答复亦为「对，用 Rate」。
 *
 * <p>样本取自生产实打（酒店 61497910 沙非大叻，2026-08-23 一晚）：
 * Member 193.40 / Cost 185.40 / Rate 185.40 / MinRate 161.97。Member 与 Rate 刻意不同值，
 * 这样取错字段测试必红。
 */
class ElongSettlePriceBasisTest {

    private static ElongNightlyRate nightly(String member, String cost, String rate, String minRate) {
        ElongNightlyRate n = new ElongNightlyRate();
        n.setDate("2026-08-23T00:00:00+08:00");
        n.setMember(member == null ? null : new BigDecimal(member));
        n.setCost(cost == null ? null : new BigDecimal(cost));
        n.setRate(rate == null ? null : new BigDecimal(rate));
        n.setMinRate(minRate == null ? null : new BigDecimal(minRate));
        return n;
    }

    @Test
    @DisplayName("每日价取 Rate，不取 Member")
    void dayPriceTakesRateNotMember() {
        List<ElongDataValidateRequest.DayPrice> dayPrices =
                ElongPriceServiceImpl.buildDayPrices(List.of(nightly("193.40", "185.40", "185.40", "161.97")));

        assertThat(dayPrices).hasSize(1);
        assertThat(dayPrices.get(0).getPrice()).isEqualByComparingTo("185.40");
        assertThat(dayPrices.get(0).getPrice()).isNotEqualByComparingTo("193.40");
        assertThat(dayPrices.get(0).getMinRate()).isEqualByComparingTo("161.97");
        assertThat(dayPrices.get(0).getDate()).isEqualTo("2026-08-23");
    }

    @Test
    @DisplayName("税费 = Rate − MinRate，房费 = MinRate，三项自洽")
    void taxIsRateMinusMinRate() {
        List<ElongDataValidateRequest.DayPrice> dayPrices =
                ElongPriceServiceImpl.buildDayPrices(List.of(nightly("193.40", "185.40", "185.40", "161.97")));

        List<PriceInfo> infos = ElongPriceServiceImpl.buildPriceInfos(dayPrices);

        assertThat(infos).hasSize(1);
        PriceInfo info = infos.get(0);
        assertThat(info.getPrice()).isEqualTo(18540);
        assertThat(info.getRoomPrice()).isEqualTo(16197);
        assertThat(info.getTaxes()).isEqualTo(2343);
        assertThat(info.getRoomPrice() + info.getTaxes()).isEqualTo(info.getPrice());
    }

    @Test
    @DisplayName("缺 Rate 时整条报价作废，不退回 Member")
    void missingRateDropsThePlanInsteadOfFallingBackToMember() {
        assertThat(ElongPriceServiceImpl.buildDayPrices(
                List.of(nightly("193.40", "185.40", null, "161.97")))).isNull();
    }

    @Test
    @DisplayName("Rate 或 MinRate 非正数同样作废——艺龙用 -1 表达不可用")
    void nonPositiveRateDropsThePlan() {
        assertThat(ElongPriceServiceImpl.buildDayPrices(
                List.of(nightly("193.40", "-1", "-1", "161.97")))).isNull();
        assertThat(ElongPriceServiceImpl.buildDayPrices(
                List.of(nightly("193.40", "185.40", "185.40", "-1")))).isNull();
    }

    @Test
    @DisplayName("多晚逐日累加，含税与税前分别求和")
    void multiNightSumsSeparately() {
        List<ElongDataValidateRequest.DayPrice> dayPrices = ElongPriceServiceImpl.buildDayPrices(List.of(
                nightly("193.40", "185.40", "185.40", "161.97"),
                nightly("202.87", "187.33", "187.33", "162.76")));

        List<PriceInfo> infos = ElongPriceServiceImpl.buildPriceInfos(dayPrices);

        assertThat(infos).hasSize(2);
        assertThat(infos.stream().mapToInt(PriceInfo::getPrice).sum()).isEqualTo(18540 + 18733);
        assertThat(infos.stream().mapToInt(PriceInfo::getRoomPrice).sum()).isEqualTo(16197 + 16276);
        assertThat(infos.stream().mapToInt(PriceInfo::getTaxes).sum()).isEqualTo(2343 + 2457);
    }
}
