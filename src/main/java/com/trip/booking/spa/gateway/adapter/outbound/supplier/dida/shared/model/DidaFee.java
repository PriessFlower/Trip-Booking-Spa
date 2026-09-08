package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 税费条目。官方 price-search 字段说明（2026-09-08 查阅）：{@code IncludedFeeList} 的金额
 * <b>已包含在 TotalPrice 里</b>，「实现的时候不要再把此税费列表里的价格跟 TotalPrice 运算」；
 * {@code ExcludedFeeList} 则是客人到店另付。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaFee {

    @JsonProperty("FeeTypeName")
    private String feeTypeName;

    @JsonProperty("Currency")
    private String currency;

    @JsonProperty("Amount")
    private BigDecimal amount;
}
