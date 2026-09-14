package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * pricesearch 响应：{@code {"Success":{"PriceDetails":{...}}}} 或 {@code {"Error":{...}}}，
 * 二者互斥（HTTP 恒 200，业务成败只看这两个节点，2026-09-08 生产实测）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaPriceSearchResponse implements BaseResponse {

    @JsonProperty("Success")
    private Success success;

    @JsonProperty("Error")
    private DidaError error;

    @Override
    public boolean isSucc() {
        return error == null && success != null;
    }

    /**
     * 无在售：整个 HotelList 为空。<b>它与"酒店 id 不存在"同形</b>——2026-09-08 实测拿不存在的
     * id 查也是 Success + 空 HotelList，道旅不为此报错。两者对上游是同一件事（这家这天没有可卖的
     * 东西），故一并落"确定无货"；能区分它们的是建档覆盖面，不是本次调用（B7）。
     */
    @Override
    public boolean isEmptyResult() {
        return success == null || success.getPriceDetails() == null
                || success.getPriceDetails().getHotelList() == null
                || success.getPriceDetails().getHotelList().isEmpty();
    }

    /** 原生错误码进 supplier_io_error_code 分布；成功时为 null */
    public String errorCode() {
        return error == null ? null : error.getCode();
    }

    public String errorMessage() {
        return error == null ? null : error.getMessage();
    }

    /** 该店的报价集合；无则 null */
    public DidaHotel firstHotel() {
        return isEmptyResult() ? null : success.getPriceDetails().getHotelList().get(0);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Success {

        @JsonProperty("PriceDetails")
        private PriceDetails priceDetails;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDetails {

        @JsonProperty("CheckInDate")
        private String checkInDate;

        @JsonProperty("CheckOutDate")
        private String checkOutDate;

        @JsonProperty("HotelList")
        private List<DidaHotel> hotelList;
    }
}
