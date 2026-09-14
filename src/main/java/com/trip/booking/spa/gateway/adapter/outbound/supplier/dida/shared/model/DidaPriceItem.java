package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 逐晚价。{@code StayDate} 形如 {@code 2026-09-29 00:00:00}（无时区，即该晚的住宿日）。
 *
 * <p>{@code MealType} 与 {@code MealAmount} 是餐食的权威字段（官方 price-search 注 8，
 * 2026-09-08 查阅：BreakfastType 已过时，且"人数跟餐数对不齐就会认为是无早"）。
 * <b>MealType 的取值表官方文档未给</b>，实测见 {@code DidaProductKeyDeriver#convertMeal}。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaPriceItem {

    @JsonProperty("Price")
    private BigDecimal price;

    @JsonProperty("StayDate")
    private String stayDate;

    @JsonProperty("MealType")
    private Integer mealType;

    /** 含餐份数；官方注 8：理论上总是小于或等于入住人数 */
    @JsonProperty("MealAmount")
    private Integer mealAmount;
}
