package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 一家酒店的报价（官方 05-product-price 的 {@code HotelList} 元素）。本仓一次只问一家 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyHotel {

    @JsonProperty("hotelId")
    private String hotelId;

    @JsonProperty("hotelNameCN")
    private String hotelNameCn;

    @JsonProperty("hotelNameEN")
    private String hotelNameEn;

    @JsonProperty("roomTypeList")
    private List<ClwyRoomType> roomTypeList;
}
