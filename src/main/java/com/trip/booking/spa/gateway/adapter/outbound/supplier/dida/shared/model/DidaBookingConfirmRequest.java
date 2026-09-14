package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * HotelBookingConfirm（下单）请求体（官方 booking-api/booking-confirm，2026-09-13 查阅）。
 *
 * <p>「创建订单前必须获得订单参考号（{@code ReferenceNo}），参考号可在价格确认接口获得。
 * 订单参数如入住日期，房间数，人数等参数必须与订单确认中的参数保持一致」——故住期、间数、
 * 占用一律从验价句柄回放，不取上游传参（不一致即 3001/3005/3008）。
 *
 * <p>{@code ClientReference}=我方单号：「贵方的订单号跟我们的订单号存在一对一的绑定关系，
 * 贵方不能使用同一个订单号来创建两个或以上的道旅订单」——它同时是幂等闸（重发同号报 3019）
 * 与按我方单号反查的坐标（HotelBookingSearch 的 BookingInfo.ClientReference）。
 *
 * <p>{@code PaymentInfo} 不发：「使用额度支付的不需要传此字段」，本账号走信用额度（额度不足报 3014）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaBookingConfirmRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("ReferenceNo")
    private String referenceNo;

    /** yyyy-MM-dd */
    @JsonProperty("CheckInDate")
    private String checkInDate;

    @JsonProperty("CheckOutDate")
    private String checkOutDate;

    @JsonProperty("NumOfRooms")
    private Integer numOfRooms;

    @JsonProperty("GuestList")
    private List<DidaGuestRoom> guestList;

    @JsonProperty("Contact")
    private DidaContact contact;

    @JsonProperty("ClientReference")
    private String clientReference;

    /** 特殊需求，「我们会尽力满足客人的需求，但无法保证能完全满足」；上游契约无此项，恒不发 */
    @JsonProperty("CustomerRequest")
    private String customerRequest;
}
