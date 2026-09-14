package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 下单前校验请求体（官方 hotel.oversea.order.check，2026-09-14 查阅，限流 1000 次/分钟）。
 *
 * <p>接口说明是"是否可预订校验、库存校验，校验成功时返回最新价格"——<b>不建单、不占房</b>，
 * 故可用作验价。与报价档的差别是它<b>带间数</b>，而间数会改变退改罚金的口径（不改变单价），
 * 见 {@code MeituanCheckPriceServiceImpl}。
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeituanOrderCheckRequest {

    private Long hotelId;

    /** 产品 ID，即报价档的 goodsId */
    private Long goodsId;

    private String checkinDate;

    private String checkoutDate;

    /** <b>每间房</b>成人数 */
    private Integer numberOfAdults;

    /** <b>每间房</b>儿童数 */
    private Integer numberOfChildren;

    /** 儿童年龄，英文逗号分隔 */
    private String childrenAges;

    /** 预订间数。报价档没有这个字段 */
    private Integer roomNum;

    private String currencyCode;

    private String clientNationality;
}
