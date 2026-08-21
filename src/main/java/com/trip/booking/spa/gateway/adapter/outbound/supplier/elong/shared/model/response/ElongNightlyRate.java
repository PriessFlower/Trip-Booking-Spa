package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.detail 的每日价（NightlyRates 子项）。
 *
 * <p><b>一晚上有四个价，口径互不相同，混用即资损</b>。以下定义逐条引自艺龙开放平台官方
 * 文档（open.elong.com/doc/info/cn-api-search-hotel_detail，2026-08-21 核对）：
 *
 * <ul>
 *   <li>{@code Member} <b>会员价</b>——"已经通过DRR的计算<b>可以直接显示给客人</b>。
 *       价格为-1表示不能销售。"即<b>对客展示口径</b>。</li>
 *   <li>{@code Cost} <b>结算价</b>——"仅用于结算价模式下的预付产品可用，
 *       <b>非结算价模式下返回-1</b>。"即<b>我方应付口径</b>。本账号实测有真实值
 *       （非 -1），故本账号属结算价模式。</li>
 *   <li>{@code Rate} 每晚每间房价（<b>含</b>税费），仅国际/港澳台产品下发。</li>
 *   <li>{@code MinRate} 最小价（<b>不含</b>税费），仅国际/港澳台产品下发。</li>
 * </ul>
 *
 * <p><b>我方是国际分销商</b>，故按 hotel.order.create 官方备注，申报总价应取
 * {@code sum(Rate) * 房间数}——不是 {@code sum(Member)}。2026-08-21 生产实测 837 个
 * 报价：{@code Rate} 与 {@code Cost} 恒相等，而 {@code Member / Cost} 落在
 * <b>1.014 ~ 1.100</b>（即会员价比结算价高 1.4%~10%，逐产品不同）。
 *
 * <p><b>当前实现与上述口径的两处偏差（本次仅改名与注释，未动取值）</b>：
 * <ul>
 *   <li>申报给艺龙的总价取 {@code sum(Member)}（见 {@code ElongPriceServiceImpl}
 *       的 {@code declaredTotalYuan}），按官方备注应为 {@code sum(Rate)}。
 *       <b>结算按申报值，已由对账单坐实</b>：2026-07 月结单，艺龙订单
 *       101000106416（沙非大叻酒店，HotelCode 61544552）「艺龙卖价 / 分销商卖价 /
 *       结算金额」三列均为 205.79，等于我方当时申报的 {@code sum(Member)}；同酒店
 *       同期实测 {@code Member/Rate} 为 1.043~1.083，故该单多付约 10~15 元。
 *       此偏差方向是<b>我方多付，每单 1.4%~10%</b>。</li>
 *   <li>验价回给上游的 {@code salePrice} 取验价响应的 {@code Rate}（结算口径），
 *       却被当作售价——方向是我方少收。</li>
 * </ul>
 * 两处均待商务确认合约口径后单独修，不在改名这次动。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongNightlyRate {

    /** 可能带时区偏移，消费时取前 10 位（yyyy-MM-dd） */
    private String date;

    /** <b>会员价</b>（元）：对客展示口径，可直接示客；-1 表示不可售。详见类 javadoc */
    private BigDecimal member;

    /** <b>结算价</b>（元）：我方应付口径；非结算价模式返回 -1。详见类 javadoc */
    private BigDecimal cost;

    /** 当日可售 */
    private Boolean status;

    /**
     * 最小价（元，<b>不含</b>税费），仅国际/港澳台下发。
     *
     * <p>验价 DayPriceList 里<b>国际必传、国内禁传</b>（官方原文）。当前
     * {@code buildDayPrices} 无条件携带，且任一晚缺此值即整条报价作废——现网清单全为
     * 国际酒店故未暴露，一旦接入国内酒店会整片报价消失。
     */
    private BigDecimal minRate;

    /**
     * 每晚每间房价（元，<b>含</b>税费），仅国际/港澳台下发。
     *
     * <p><b>国际分销商的申报总价基准</b>：官方要求 {@code sum(Rate) * 房间数}。
     * 实测与 {@code cost} 恒相等。
     */
    private BigDecimal rate;

    /** 历史早餐字段，部分产品仍下发；缺席时以 meals.dayMealTable 为准 */
    private Integer breakfastCount;
}
