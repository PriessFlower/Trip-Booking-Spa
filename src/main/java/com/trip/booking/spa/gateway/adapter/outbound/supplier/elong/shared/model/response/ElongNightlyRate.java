package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** hotel.detail 的每日价（NightlyRates 子项） */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongNightlyRate {

    /** 可能带时区偏移，消费时取前 10 位（yyyy-MM-dd） */
    private String date;

    /** 售价（元） */
    private BigDecimal member;

    /** 结算价（元） */
    private BigDecimal cost;

    /** 当日可售 */
    private Boolean status;

    /** 验价 DayPriceList 必传 */
    private BigDecimal minRate;

    private BigDecimal rate;

    /** 历史早餐字段，部分产品仍下发；缺席时以 meals.dayMealTable 为准 */
    private Integer breakfastCount;
}
