package com.bingo.hotel.spa.intl.cli.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRespDTO {

    public String hotelId;
    public String productId;
    public Integer supplierId;
    /**
     * 总价
     */
    private Integer totalPrice;
    /**
     * 外币币种
     */
    private String currencyType;
    /**
     * 产品基本信息
     */
    public ProductInfo productInfo;
    /**
     * 总价
     */
    public Room room;
    /**
     * 外币币种
     */
    public String currency;
    /**
     * 规则
     */
    public List<BookingRule> bookingRule;
    /**
     * 餐食
     */
    public Meal meal;
    /**
     * 取消政策
     */
    public List<CancelPolicy> cancelPolicy;
    /**
     * 价格
     */
    public List<PriceInfo> priceInfos;

}
