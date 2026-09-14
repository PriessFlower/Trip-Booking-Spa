package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一条报价（pricesearch 与 PriceConfirm 同构，后者少了取消政策——那一档挂在酒店节点上）。
 *
 * <p>两个 id 的分工（腐性申报见 {@code SupplierIdentityProfile.DIDA}）：
 * {@code RoomTypeID} 是稳定房型 id（实测即静态内容接口发布的房型主键），进 productKey；
 * {@code RatePlanID} 是易腐报价码（实测 3 秒即换代），只进 OfferStore。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaRatePlan {

    @JsonProperty("RoomTypeID")
    private Long roomTypeId;

    @JsonProperty("RoomName")
    private String roomName;

    @JsonProperty("RoomName_CN")
    private String roomNameCn;

    @JsonProperty("RatePlanID")
    private String ratePlanId;

    @JsonProperty("RatePlanName")
    private String ratePlanName;

    /** 床型 id。官方注 13 要求必须解析并展示，以减少争议单；本批只透出不解释 */
    @JsonProperty("BedType")
    private Integer bedType;

    /** 已过时（官方注 8），餐食判定不读它，保留仅为排障对照 */
    @JsonProperty("BreakfastType")
    private Integer breakfastType;

    /** 官方字段说明：「仅供参考，不准的。供应商自身就无法提供准确的数字」 */
    @JsonProperty("InventoryCount")
    private Integer inventoryCount;

    @JsonProperty("Currency")
    private String currency;

    /**
     * 总价。<b>pricesearch 是单间价、PriceConfirm 是全部房间的总价</b>
     * （官方 price-search 注：「如搜索 3 间，需将单价 ×3」）。
     */
    @JsonProperty("TotalPrice")
    private BigDecimal totalPrice;

    @JsonProperty("IsOnRequest")
    private Boolean isOnRequest;

    /** 官方注 11：pricesearch 给了就必须原样回传给 PriceConfirm，禁止解码或改写 */
    @JsonProperty("Metadata")
    private String metadata;

    @JsonProperty("RoomOccupancy")
    private DidaRoomOccupancy roomOccupancy;

    @JsonProperty("PriceList")
    private List<DidaPriceItem> priceList;

    @JsonProperty("RatePlanCancellationPolicyList")
    private List<DidaCancellationPolicy> ratePlanCancellationPolicyList;

    @JsonProperty("IncludedFeeList")
    private List<DidaFee> includedFeeList;

    @JsonProperty("ExcludedFeeList")
    private List<DidaFee> excludedFeeList;

    /** 非即时确认报价（询价房源）。本批只卖即时确认，故它是转换与找票的排除项 */
    public boolean onRequest() {
        return Boolean.TRUE.equals(isOnRequest);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DidaRoomOccupancy {

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
