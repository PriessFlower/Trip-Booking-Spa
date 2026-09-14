package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 一晚的价格，单位<b>分</b>，口径「每间每晚」。
 *
 * <p>2026-09-14 生产实测（酒店 2388500，两晚）：同一产品问 1 间与问 2 间，逐日价一模一样
 * （11655/11655 对 11636/11636，差异是两次调用之间的真实价格变动，不是乘间数），
 * 故本仓的住期总价 = Σ逐日价 × 间数（B4）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanPriceModel {

    /** yyyy-MM-dd */
    @JsonProperty("date")
    private String date;

    @JsonProperty("price")
    private Long price;

    /** 活动价；实测与 price 相等，本仓不用它报价，留着是为了出现背离时能看见 */
    @JsonProperty("activityPrice")
    private Long activityPrice;
}
