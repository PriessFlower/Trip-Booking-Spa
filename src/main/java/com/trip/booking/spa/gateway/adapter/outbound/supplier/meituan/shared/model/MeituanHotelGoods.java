package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 一家酒店的产品袋 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanHotelGoods {

    @JsonProperty("hotelId")
    private Long hotelId;

    @JsonProperty("goodsList")
    private List<MeituanGoods> goodsList;
}
