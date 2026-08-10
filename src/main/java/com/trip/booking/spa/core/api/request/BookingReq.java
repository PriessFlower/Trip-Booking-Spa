package com.trip.booking.spa.core.api.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
public class BookingReq {

    @NonNull
    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店ID

    private String sProductId;//供应商产品Id
    @NonNull
    private String orderId;//自有订单Id
    @NonNull
    private String personName;//入住人
    @NonNull
    private String contactName;//联系人名称
    @NonNull
    private String contactPhone;//联系人电话
    @NonNull
    private String checkIn;//入住日期
    @NonNull
    private String checkOut;//离店日期
    @NonNull
    private Integer roomNum;//房间数量
    @NonNull
    private Integer totalPrice;//总价
    @NonNull
    private Integer settlePrice;//结算价

    /**
     * 验价时返回的预订令牌，即 {@code CheckPriceRespDTO.prebookToken}，原样回传。
     *
     * <p>Expedia 场景下这是下单的必要输入：令牌自带本次报价的全部上下文，下单即向该地址提交。
     * 设计上由调用方持有并回传，而不由本服务重新验价获取——重新验价会得到新的报价，
     * 可能与调用方已向旅客展示的价格不一致。
     *
     * <p>令牌有时效，过期后下单会被拒；此时调用方应重新验价再下单。
     *
     * <p><b>约束</b>：本字段语义固定为「验价令牌」。各供应商的令牌形态不同（Expedia 是
     * 带 token 的 book 链接，其他家可能是 hash 或 session 串），但含义必须一致。
     * 旧系统同名字段曾被 6 家供应商赋予 6 种不同语义，导致无法从字段本身判断该怎么用——
     * 新增供应商时禁止复用本字段表达其他东西，需要别的输入请另加字段。
     */
    private String prebookToken;

    /**
     * 入住人名（英文或拼音），可选；缺省时从 {@link #personName} 拆分。
     *
     * <p>Expedia 用旅客姓名对照 UN／UK／EU 制裁名单筛查，属强制合规项，
     * 故须提交真实姓名，禁止填占位值。
     */
    private String givenName;

    /** 入住人姓（英文或拼音），可选；缺省时从 {@link #personName} 拆分 */
    private String familyName;

}
