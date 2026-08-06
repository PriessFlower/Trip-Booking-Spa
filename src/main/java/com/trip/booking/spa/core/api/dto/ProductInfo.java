package com.trip.booking.spa.core.api.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {

    /**
     * 是否立即确认：
     * 0-非立即确认；
     * 1-立即确认；
     */
    public Integer confirmType;
    /**
     * 库存
     */
    public Integer inventory;
    /**
     * 房态：
     * 0-满房；
     * 1-可预订；
     */
    public Integer productStatus;
    /**
     * 产品支付方式：
     * 0-预付；
     * 1-担保；
     * 2-现付非担保；
     */
    public Integer paymentType;
    /**
     * 产品名称
     */
    public String productName;
    /**
     * 产品限制规则
     */
    public String productLimitRule;
    /**
     * 产品是否需要身份证：
     * true-需要；
     * false-不需要；
     */
    public Boolean needCertificate;
}
