package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 一条取消政策：<b>从 {@code FromDate} 起</b>取消收 {@code Amount}。
 *
 * <p>官方 price-search 字段说明（2026-09-08 查阅）：FromDate 是「此取消政策生效的起始日期。
 * 注意了，是<b>北京时间</b>，不是酒店的当地时间」；FromDate 之前取消则免费。
 * {@code Amount} 的币种即本次报价币种（{@code supplier.dida.currency}）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaCancellationPolicy {

    /** ISO 带偏移时刻，如 {@code 2026-09-26T00:00:00+08:00} */
    @JsonProperty("FromDate")
    private String fromDate;

    @JsonProperty("Amount")
    private BigDecimal amount;
}
