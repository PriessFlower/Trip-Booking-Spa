package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 报价／试单响应（官方 05-product-price）。
 *
 * <p><b>{@code code=200} 与空列表在本家不共存</b>：cursor 生产一周（2026-09-07~14）5,776 次
 * {@code code=200} 里空 {@code hotelList} <b>零次</b>，没货一律走 {@code code=500} +
 * {@code message="No Availability"}（427/427，是该周 500 的唯一文案）。故
 * {@link #isEmptyResult()} 是<b>报价档表达"这住期没货"的常规形态</b>，不是异常：2026-09-14
 * 生产实测同一家酒店 T+1/T+60 回 hotelList 各 1 家，T+200/T+330 回 {@code code=200} 且
 * hotelList 为 <b>null</b>（不是空数组），无任何 message。
 *
 * <p>此前这里写的是"契约上不该出现"，依据是 cursor 一周 5,776 次 200 里空列表 0 次——
 * 那批流量全是<b>验价档</b>（带 RatePlanId，点的是已知在售的票），本就不可能空；拿它去推
 * 报价档是取样偏差。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyPriceResponse implements BaseResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("hotelList")
    private List<ClwyHotel> hotelList;

    /** 试单（传了 RatePlanId）时才回；下单必填，10 分钟有效，下单后即过期 */
    @JsonProperty("rateKey")
    private String rateKey;

    @Override
    public boolean isSucc() {
        return Integer.valueOf(ClwyCodes.OK).equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return hotelList == null || hotelList.isEmpty();
    }

    /** 无库存：本家唯一有实证的失败文案，见 {@link ClwyCodes#NO_AVAILABILITY} */
    public boolean isNoAvailability() {
        return !isSucc() && ClwyCodes.NO_AVAILABILITY.equalsIgnoreCase(message == null ? null : message.trim());
    }

    public ClwyHotel firstHotel() {
        return isEmptyResult() ? null : hotelList.get(0);
    }
}
