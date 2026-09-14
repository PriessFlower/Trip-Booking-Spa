package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 一条取消政策（{@code CancellationPenalties} 元素）：<b>从 {@code from} 起</b>取消收 {@code amount}，
 * 之前免费。起始点列表的形状与道旅同，但<b>时刻的表达方式与文档写的不一样</b>。
 *
 * <p><b>2026-09-14 生产实测（8 家酒店 883 条报价）</b>，三处与文档/旧样例不符，逐条记下：
 * <ul>
 *   <li>{@code from} <b>不带偏移量</b>，是 {@code "2026-09-13 23:00:00"} 这种本地时刻
 *       （空格分隔，非 ISO）。cursor 注释里留的 {@code "2025-10-26T00:00:00+08:00"} 是它自己
 *       归一后的形状，不是线上回的</li>
 *   <li>{@code cityTimeZone} <b>不是时区名而是小时偏移</b>，实测取值 {@code "1"}/{@code "-8"}/{@code "12"}。
 *       按 IANA 名解析（{@code ZoneId.of}）会全部抛异常</li>
 *   <li>多出一个文档没有的 {@code newFrom}，是 {@code from} 拼上偏移，如
 *       {@code "2026-09-13T23:00:00+1:00"}——注意偏移是<b>单位数</b>，ISO 要求两位，
 *       {@code OffsetDateTime.parse} 认不了</li>
 * </ul>
 * 故本仓的解析口径是 <b>{@code from}（本地）+ {@code cityTimeZone}（小时偏移）</b>：两者都是
 * 官方文档里有的字段，语义明确；{@code newFrom} 只作兜底，见 {@code ClwyProductKeyDeriver}。
 *
 * <p>{@code before}/{@code type}/{@code value} 三个字段线上也在下发（实测恒为 0），文档只字未提，
 * 语义不明——<b>一律不读</b>。它们长得像我方 CancelPolicy 的同名字段，容易让人以为可以直接拿来用。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyCancellationPenalty {

    /** 本地时刻，形如 {@code 2026-09-13 23:00:00}（无偏移，见类注释） */
    @JsonProperty("from")
    private String from;

    /**
     * 文档未记载的字段：{@code from} 拼上小时偏移，如 {@code 2026-09-13T23:00:00+1:00}。
     * 偏移是单位数，不合 ISO——只作兜底，且用前要补零。
     */
    @JsonProperty("newFrom")
    private String newFrom;

    @JsonProperty("amount")
    private BigDecimal amount;
}
