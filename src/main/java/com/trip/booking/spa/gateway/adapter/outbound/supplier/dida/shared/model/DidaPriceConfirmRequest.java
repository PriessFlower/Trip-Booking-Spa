package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * PriceConfirm（验价）请求体（官方 booking-api/price-confirm，2026-09-08 查阅）。
 *
 * <p>{@code PreBook=true} 才回 {@code ReferenceNo}（下单要用，有效期 2 小时，见错误码 3006），
 * 故下单前那一档必须置 true。
 *
 * <p>{@code Metadata} 文档标为必填，但 2026-09-08 生产实测：本账号的 pricesearch 响应<b>不带</b>
 * 该字段，不传照样验价成功（HotelID=528 实打通过）。官方注 11 说明它需由客户经理开通，
 * 用途是道旅侧串联查价与验价做准确率分析。故此处「有就回传、没有就不传」。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaPriceConfirmRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("HotelID")
    private Long hotelId;

    @JsonProperty("RatePlanID")
    private String ratePlanId;

    @JsonProperty("CheckInDate")
    private String checkInDate;

    @JsonProperty("CheckOutDate")
    private String checkOutDate;

    @JsonProperty("Nationality")
    private String nationality;

    @JsonProperty("NumOfRooms")
    private Integer numOfRooms;

    @JsonProperty("Currency")
    private String currency;

    @JsonProperty("PreBook")
    private Boolean preBook;

    @JsonProperty("IsNeedOnRequest")
    private Boolean isNeedOnRequest;

    @JsonProperty("Metadata")
    private String metadata;

    @JsonProperty("OccupancyDetails")
    private List<OccupancyDetail> occupancyDetails;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OccupancyDetail {

        /** 房间号，从 1 开始 */
        @JsonProperty("RoomNum")
        private Integer roomNum;

        @JsonProperty("AdultCount")
        private Integer adultCount;

        @JsonProperty("ChildCount")
        private Integer childCount;

        @JsonProperty("ChildAgeDetails")
        private List<Integer> childAgeDetails;
    }
}
