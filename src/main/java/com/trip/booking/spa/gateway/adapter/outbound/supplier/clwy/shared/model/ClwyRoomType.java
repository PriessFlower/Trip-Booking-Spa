package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一个房型（{@code RoomTypeList} 元素）。{@code roomTypeId} 是房型级目录的锚，
 * 腐性申报见 {@code SupplierIdentityProfile.CLWY}。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyRoomType {

    @JsonProperty("roomTypeId")
    private String roomTypeId;

    @JsonProperty("roomTypeNameCN")
    private String roomTypeNameCn;

    @JsonProperty("roomTypeNameEN")
    private String roomTypeNameEn;

    /** 格式「数量*床型」，如 {@code 1*大床}。本批只原样透出，不解析 */
    @JsonProperty("bedType")
    private String bedType;

    /** 0 有窗 1 部分有窗 2 无窗 3 未知 */
    @JsonProperty("window")
    private Integer window;

    /** 0 无法上网 1 无线WIFI 2 有线宽带 3 两者 4 未知 */
    @JsonProperty("internetWay")
    private Integer internetWay;

    /** 0 表示未知 */
    @JsonProperty("maxAdults")
    private Integer maxAdults;

    /** 0 表示未知 */
    @JsonProperty("maxChildren")
    private Integer maxChildren;

    @JsonProperty("useableArea")
    private String useableArea;

    @JsonProperty("floor")
    private String floor;

    @JsonProperty("ratePlanList")
    private List<ClwyRatePlan> ratePlanList;
}
