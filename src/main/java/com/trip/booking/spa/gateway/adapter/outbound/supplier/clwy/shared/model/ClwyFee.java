package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 到店付的费或税（{@code FeeList} 元素，2025.01.02 起新增）。<b>不含在报价里</b>，客人到店另付，
 * 故不参与总价运算——与道旅的 {@code ExcludedFeeList} 同位。
 *
 * <p><b>币种字段线上有两个</b>（2026-09-14 实测报文）：官方参数表写的是拼错的 {@code Curreny}
 * （少一个 c），而实际响应里 {@code curreny} 与 {@code currency} <b>同时存在、取值相同</b>。
 * 两个都映射、取用时优先拼写正确的那个——只认文档那个会在供应商哪天修掉拼写时静默变 null，
 * 只认正确的那个则在旧版本上拿不到值。wire 字面量按 §4.2.3 原样保留，不"纠正"任何一个。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyFee {

    @JsonProperty("feeName")
    private String feeName;

    @JsonProperty("feePrice")
    private BigDecimal feePrice;

    /** 官方参数表的拼写（少一个 c），wire 字面量原样保留（§4.2.3） */
    @JsonProperty("curreny")
    private String curreny;

    /** 线上实际也在下发的正确拼写，与上者同值 */
    @JsonProperty("currency")
    private String currency;

    /** 取币种：优先正确拼写，回落官方那个拼错的，见类注释 */
    public String currencyOf() {
        return currency != null && !currency.isBlank() ? currency : curreny;
    }
}
