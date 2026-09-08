package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * pricesearch 请求体（官方 booking-api/price-search，2026-09-08 查阅）。
 *
 * <p><b>一次只问一家酒店</b>：{@code HotelIDList} 虽收列表，但<b>多店 + {@code IsRealTime=true}
 * 这个组合会被道旅降级</b>——2026-09-08 生产实测（10 家酒店、同一住期、单店与批量交替三轮，
 * 逐店结果两次完全可复现）：逐店实时共回 280 条报价，10 家一批实时只回 105 条（38%），
 * 其中 4 家整家不出现在响应里，3 家最低价被抬高（如 610→634）。
 * 同一批 10 家改成 {@code IsRealTime=false} 则回满 280 条，逐家条数与最低价与逐店实时完全一致
 * ——所以缩水不是"批量上限"，是实时聚合在多店请求下拿多少算多少。
 * 本仓选逐店 + 实时：单店实时 ≥ 单店非实时（8 家里 7 家完全相同，1 家实时多 3 条），是最全的一档。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaPriceSearchRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("HotelIDList")
    private List<Long> hotelIdList;

    /** yyyy-MM-dd */
    @JsonProperty("CheckInDate")
    private String checkInDate;

    @JsonProperty("CheckOutDate")
    private String checkOutDate;

    @JsonProperty("Currency")
    private String currency;

    /** ISO 3166-1 alpha-2，必填，语义见 DidaProperties#nationality */
    @JsonProperty("Nationality")
    private String nationality;

    @JsonProperty("IsRealTime")
    private IsRealTime isRealTime;

    @JsonProperty("RealTimeOccupancy")
    private RealTimeOccupancy realTimeOccupancy;

    /** false=不要非即时确认报价。本批只卖即时确认（下单链路未接，见 architecture.md §7） */
    @JsonProperty("IsNeedOnRequest")
    private Boolean isNeedOnRequest;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IsRealTime {

        /** true=要求道旅回实时价而非其缓存价 */
        @JsonProperty("Value")
        private Boolean value;

        @JsonProperty("RoomCount")
        private Integer roomCount;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RealTimeOccupancy {

        @JsonProperty("AdultCount")
        private Integer adultCount;

        @JsonProperty("ChildCount")
        private Integer childCount;

        @JsonProperty("ChildAgeDetails")
        private List<Integer> childAgeDetails;
    }
}
