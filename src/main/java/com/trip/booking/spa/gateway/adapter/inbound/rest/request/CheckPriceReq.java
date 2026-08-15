package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
public class CheckPriceReq {
    @NonNull
    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店ID

    private String sProductId;//供应商产品Id

    /**
     * 网关派生的稳定产品身份（查价响应透出的 productKey，docs/product-identity.md R-1.1）。
     * 可选：携带时，若 sProductId 所指报价已不在（令牌死），网关可按它在现货中
     * 自动换等价新票（resolve ②）；不带则维持旧行为（RATE_DEAD）。
     */
    private String productKey;
    @NonNull
    private String checkIn;//入住日期
    @NonNull
    private String checkOut;//离店日期
    @NonNull
    private Integer roomNum;//房间数量
    @NonNull
    private Integer totalPrice;//总价

    private String planSession;

    private String sCityCode;

    private Integer adultCount;

    private Integer childNum; //儿童数

    private List<Integer> childAges; //儿童年龄

    private String priceFlag;//hotel_package-打包价 hotel_only-零售价

    private String language;//语言

    private String bedId;

    private String currency;//分销商币种
}
