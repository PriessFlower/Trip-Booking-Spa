package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceInfoCache implements Serializable{
    private static final long serialVersionUID = 7009692878190650106L;

    /**
     * 日期
     */
    private String date;

    /**
     * 价格：总费用
     */
    private Integer price;

    /**
     * 税费 expedia专用
     */
    private Integer taxes;

    /**
     * 房价 expedia专用
     */
    private Integer roomPrice;

    /**
     * 酒店一次性收取费用 每日总价+酒店一次性收取费用=线上支付总价 expedia专用
     */
    private Integer stayPrice;

    /**
     * 线下支付金额 expedia专用
     */
    private Integer storePayPrice;

    /**
     * 佣金
     */
    private Integer brokerage;

}
