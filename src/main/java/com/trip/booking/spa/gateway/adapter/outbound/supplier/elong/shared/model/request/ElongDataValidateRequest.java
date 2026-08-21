package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * hotel.data.validate（验价）请求体。字段拼写以 docs/elong/hotel.data.validate.json
 * 抓包为准：{@code RoomTypeID}（ID 全大写）、{@code LittleMajiaId}（与 hotel.detail
 * 响应里的 {@code Littlemajiaid} 拼写不同）、DayPriceList 子项键名是 {@code Date}
 * （写成 Day 报 H001188）。
 *
 * <p>LittleMajiaId 与 GoodsUniqId 必须来自<b>本次会话刚取回的</b> hotel.detail 响应
 * （现取现验，R-3.1）：cursor 复用隔时快照凭证实测 45/47 全灭（H001144）。
 */
@Getter
@Setter
@Builder
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElongDataValidateRequest {

    /** yyyy-MM-dd */
    private String arrivalDate;

    /** yyyy-MM-dd */
    private String departureDate;

    /** yyyy-MM-dd HH:mm:ss */
    private String earliestArrivalTime;

    /**
     * yyyy-MM-dd HH:mm:ss。固定取入住日 23:59:59：上游若透传自家到店时间
     * （如 amap.EarlyArrivalTime）会误伤可订性判定（移植风险⑩）。
     */
    private String latestArrivalTime;

    private String hotelId;

    private String hotelCode;

    private Long ratePlanId;

    private String roomTypeID;

    private String littleMajiaId;

    private String goodsUniqId;

    private String shopperProductId;

    private String subSupplierId;

    private String supplierId;

    /**
     * <b>申报总价</b>（元，艺龙口径），须与 DayPriceList 各日 Price 之和一致。
     *
     * <p>Java 字段名刻意不叫 {@code totalPrice}：全仓有五个 {@code totalPrice}，分属
     * 「对客所见价」「对客展示价」「申报价」三种口径，混用已致两处资损（见
     * {@code ElongNightlyRate} 类 javadoc）。此处是<b>我方申报给艺龙</b>的那个数。
     * 线上键名由 {@code @JsonProperty} 钉死为 {@code TotalPrice}，与 wire 契约无关。
     *
     * <p>艺龙对本字段<b>只校下限</b>（2026-08-21 实测）：≥ 当日 {@code Cost} 即
     * {@code ResultCode=OK}，报高不拦（+5% 仍 OK）；低于 {@code Cost} 报
     * {@code ResultCode=Rate} 与 {@code H001189|每日价传参异常}。
     */
    @JsonProperty("TotalPrice")
    private BigDecimal declaredTotal;

    private Integer numberOfRooms;

    private Integer numberOfAdults;

    private List<Integer> childAges;

    @Builder.Default
    private String currencyCode = "RMB";

    @Builder.Default
    private List<String> nat = List.of("CN");

    private List<DayPrice> dayPriceList;

    /** 每日价，取自同会话 hotel.detail 的 NightlyRates（Member 售价 / MinRate） */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DayPrice {

        /** yyyy-MM-dd。键名必须是 Date，写成 Day 报 H001188 */
        private String date;

        /** 元 */
        private BigDecimal price;

        /** 元，validate 必传 */
        private BigDecimal minRate;
    }
}
