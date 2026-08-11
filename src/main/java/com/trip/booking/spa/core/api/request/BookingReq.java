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
     * 验价时由网关签发的报价句柄，即 {@code CheckPriceRespDTO.offerId}，原样回传。
     *
     * <p>这是下单的必要输入。句柄背后是供应商内部的下单凭据，由网关自持——
     * <b>调用方不应知道该凭据的形态，也不应尝试解析本字段</b>。
     *
     * <p>为什么不由本服务重新验价取凭据：重新验价会得到新的报价，可能与调用方已向旅客
     * 展示的价格不一致。故凭据必须来自调用方展示给旅客的那一次验价。
     *
     * <p>句柄有时效。过期后下单会得到确定性失败（供应商侧不会发生任何事），
     * 此时调用方重新验价再下单即可。
     *
     * <p><b>约束</b>：本字段语义固定为「验价所得的报价句柄」，取值只能来自本服务签发。
     * 旧系统的同位字段直接透传供应商凭据，被 6 家供应商赋予 6 种语义，
     * 导致无法从字段本身判断该怎么用——新增供应商时禁止改变本字段语义，
     * 需要别的输入请另加字段。
     */
    private String offerId;

}
