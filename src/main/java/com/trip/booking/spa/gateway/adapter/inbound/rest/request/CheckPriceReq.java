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
    /**
     * 上游展示价（分）。<b>可选</b>——它的唯一用途是 resolve 换票时的容差基准，
     * 而基准价网关自己就有（刷价写入的产品详情缓存）。
     *
     * <p>曾经是必填（Lombok {@code @NonNull} → 反序列化即抛 → HTTP 400）。但接入方
     * 未必持有价格：cursor 的验价请求 DTO 就没有价格字段（老路价格从它自己的库里查），
     * 2026-08-19 因此把 spa# 票据验价全打成 400。网关是报价的权威，不该要求调用方
     * 把网关自己发的价再告诉它一遍。缺失时按 sProductId 反查缓存原价作基准（见
     * {@code ElongPriceServiceImpl#lookupTotalPriceFromCache}）；反查不到则不换票
     * （无基准即无从判断价格漂移，R-1.6 宁可少卖不可卖错）。
     */
    private Integer totalPrice;

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
