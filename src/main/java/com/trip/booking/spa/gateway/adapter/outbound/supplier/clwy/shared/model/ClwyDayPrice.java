package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 逐晚价（{@code RatePlanPriceList} 元素）。
 *
 * <p><b>{@code price} 是「每天每间」价</b>（官方字段说明原文），故整单总价 = Σ逐日价 × 间数。
 * 这一条是 B4 的原点：cursor 侧曾按单间口径落成本，多间订单的成本记账因此出错
 * （docs/gateway-boundary.md B4）。
 *
 * <p>{@code flag}：1 上架 0 下架。任一晚下架，这条报价这段住期就不可售。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyDayPrice {

    /** yyyy-MM-dd */
    @JsonProperty("date")
    private String date;

    /** 每天每间价 */
    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("currency")
    private String currency;

    /** 可售间数 */
    @JsonProperty("roomCount")
    private Integer roomCount;

    /** 1 上架 0 下架 */
    @JsonProperty("flag")
    private Integer flag;

    public boolean onSale() {
        return flag != null && flag == 1;
    }
}
