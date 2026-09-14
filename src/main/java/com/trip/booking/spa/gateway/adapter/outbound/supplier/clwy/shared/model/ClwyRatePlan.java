package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一条价格计划（{@code RatePlanList} 元素）——本家的「报价」单位。
 *
 * <p>两个 id 的分工（腐性申报见 {@code SupplierIdentityProfile.CLWY}）：
 * 所在房型的 {@code RoomTypeId} 是稳定房型 id，进 productKey；本类的 {@code ratePlanId}
 * 是<b>分代轮换的易腐报价码</b>（cursor 取证：60 天 58 次重放仅 4 次成功、93% 撞
 * {@code No Availability}），只进 OfferStore、禁止落库。
 *
 * <p>{@code breakfast} 是<b>早餐份数</b>（int），不是布尔——0 即无早。本家没有午/晚餐字段。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyRatePlan {

    @JsonProperty("ratePlanId")
    private String ratePlanId;

    @JsonProperty("ratePlanNameCN")
    private String ratePlanNameCn;

    @JsonProperty("ratePlanNameEN")
    private String ratePlanNameEn;

    /** 早餐份数；0 = 无早 */
    @JsonProperty("breakfast")
    private Integer breakfast;

    /** 最小连住天数，0 不限 */
    @JsonProperty("continuousDays")
    private Integer continuousDays;

    /** 最大连住天数，0 不限 */
    @JsonProperty("maxContinuousDays")
    private Integer maxContinuousDays;

    /** 提前预订天数，0 不限 */
    @JsonProperty("advanceDays")
    private Integer advanceDays;

    /** 最少预订间数，0 不限 */
    @JsonProperty("minRoomCount")
    private Integer minRoomCount;

    /** 最大预订间数，0 不限 */
    @JsonProperty("maxRoomCount")
    private Integer maxRoomCount;

    /** 最大成人数，0 未知 */
    @JsonProperty("maxAdultOccupancy")
    private Integer maxAdultOccupancy;

    /** 最大儿童数，0 未知 */
    @JsonProperty("maxChildrenOccupancy")
    private Integer maxChildrenOccupancy;

    /** 0 无 1 酒店前台开 2 我司开 */
    @JsonProperty("invoiceType")
    private Integer invoiceType;

    @JsonProperty("ratePlanPriceList")
    private List<ClwyDayPrice> ratePlanPriceList;

    @JsonProperty("cancellationPenalties")
    private List<ClwyCancellationPenalty> cancellationPenalties;

    /** 取消政策所用时区（IANA 名，如 Asia/Shanghai）。每家酒店各自不同 */
    @JsonProperty("cityTimeZone")
    private String cityTimeZone;

    /** 到店付的费或税，不含在报价内 */
    @JsonProperty("feeList")
    private List<ClwyFee> feeList;

    /** null/0 普通渠道，其他值代表特殊渠道；验价回了非 0 就要原样带回 */
    @JsonProperty("tag")
    private Integer tag;

    /** 1 是钟点房；null/0 否。本仓不卖钟点房 */
    @JsonProperty("isHourRoom")
    private Integer isHourRoom;

    @JsonProperty("hourRoomWeekIndex")
    private String hourRoomWeekIndex;

    @JsonProperty("hourRoomStartTime")
    private String hourRoomStartTime;

    @JsonProperty("hourRoomEndTime")
    private String hourRoomEndTime;

    @JsonProperty("hourRoomTimeLimit")
    private Integer hourRoomTimeLimit;

    /**
     * 是否钟点房（2026.04.09 起官方新增该资源，需运营单独开通）。
     * 本批只卖整夜房，故它是转换与找票两处的排除项——钟点房的"住期"语义与整夜房不同，
     * 按整夜房口径卖出去就是卖错。
     */
    public boolean hourRoom() {
        return isHourRoom != null && isHourRoom == 1;
    }
}
