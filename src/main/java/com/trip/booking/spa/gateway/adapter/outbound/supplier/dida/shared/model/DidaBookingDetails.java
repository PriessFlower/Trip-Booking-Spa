package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一笔道旅订单（官方 booking-api/booking-confirm 的 {@code BookingDetails} 与
 * booking-search 的 {@code BookingDetailsList} 元素同形，2026-09-13 查阅）。
 *
 * <p>{@code Status} 取值（官方 information-hub/api-booking-status）：0 PreBook、1 Booked（待支付）、
 * 2 Confirmed、3 Canceled、4 Failed、5 Pending（3 分钟内到终态）、6 OnRequest（120 分钟内到终态）。
 * <b>只有 2/3/4 是终态</b>——官方 booking-search 明示「其他任何返回结果（如空返回、或 Status 为
 * 非 2/3/4 的其他数值）均不能视为订单的最终处理结果」。
 *
 * <p>{@code ConfirmationCode} 是酒店确认号（HCN），官方注 1：不是每家酒店都回，能实时回的在
 * 下单响应里就带，否则入住前三天再查单看。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaBookingDetails {

    /** 终态取值，见类注释 */
    public static final int STATUS_PRE_BOOK = 0;
    public static final int STATUS_BOOKED = 1;
    public static final int STATUS_CONFIRMED = 2;
    public static final int STATUS_CANCELED = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_PENDING = 5;
    public static final int STATUS_ON_REQUEST = 6;

    @JsonProperty("BookingID")
    private String bookingId;

    @JsonProperty("Status")
    private Integer status;

    /** 形如 {@code 2025-12-07 00:00:00}（实测报文），官方样例 XML 版为纯日期 */
    @JsonProperty("CheckInDate")
    private String checkInDate;

    @JsonProperty("CheckOutDate")
    private String checkOutDate;

    /** 下单时刻，北京时间，形如 {@code 2025-11-28 18:26:28.510} */
    @JsonProperty("OrderDate")
    private String orderDate;

    @JsonProperty("NumOfRooms")
    private Integer numOfRooms;

    /** 整单总价（大单位），币种在 {@code Hotel.RatePlanList[].Currency}，本节点不带 */
    @JsonProperty("TotalPrice")
    private BigDecimal totalPrice;

    @JsonProperty("ClientReference")
    private String clientReference;

    /** 酒店确认号（HCN），缺席即酒店/供应商尚未提供 */
    @JsonProperty("ConfirmationCode")
    private String confirmationCode;

    /** 酒店与报价节点，与查价同构（多 CancellationPolicyList/IncludedFeeList，已在 DidaHotel 上） */
    @JsonProperty("Hotel")
    private DidaHotel hotel;

    @JsonProperty("GuestList")
    private List<DidaGuestRoom> guestList;

    @JsonProperty("Contact")
    private DidaContact contact;

    /** 报价币种：取首条 RatePlan 的 Currency；缺席返回 null——不兜成 CNY */
    public String currency() {
        if (hotel == null || hotel.getRatePlanList() == null || hotel.getRatePlanList().isEmpty()) {
            return null;
        }
        return hotel.getRatePlanList().get(0).getCurrency();
    }

    /** 是否已到官方定义的终态（2/3/4） */
    public boolean isFinal() {
        return status != null && (status == STATUS_CONFIRMED || status == STATUS_CANCELED || status == STATUS_FAILED);
    }
}
