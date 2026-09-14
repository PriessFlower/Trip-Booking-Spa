package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 一家酒店的报价集合。PriceConfirm 的同名节点多一个 {@code CancellationPolicyList}（验后条款） */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaHotel {

    @JsonProperty("HotelID")
    private Long hotelId;

    @JsonProperty("HotelName")
    private String hotelName;

    @JsonProperty("TotalPrice")
    private BigDecimal totalPrice;

    @JsonProperty("RatePlanList")
    private List<DidaRatePlan> ratePlanList;

    /** 仅 PriceConfirm 下发：验价时点的取消政策，语义同报价里的那份 */
    @JsonProperty("CancellationPolicyList")
    private List<DidaCancellationPolicy> cancellationPolicyList;
}
