package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * PriceConfirm（验价）响应。外壳与 pricesearch 同形，多一个 {@code ReferenceNo}——
 * 官方 booking-api/price-confirm（2026-09-08 查阅）：「PreBook = true 时，在 Response 中会
 * 返回一个 ReferenceNo。这个 ReferenceNo 是下一步订单创建时需要填的。」该页未提有效期；
 * 「2 小时」只见于错误码 3006 的文案，口径辨析见 {@code DidaOfferCredentials#REFERENCE_NO}。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaPriceConfirmResponse implements BaseResponse {

    @JsonProperty("Success")
    private Success success;

    @JsonProperty("Error")
    private DidaError error;

    @Override
    public boolean isSucc() {
        return error == null && success != null;
    }

    /** 空：验价成功但所点报价已不在响应里——那是该票已死，不是"没问出来" */
    @Override
    public boolean isEmptyResult() {
        return firstHotel() == null;
    }

    public String errorCode() {
        return error == null ? null : error.getCode();
    }

    public String errorMessage() {
        return error == null ? null : error.getMessage();
    }

    public String referenceNo() {
        return success == null || success.getPriceDetails() == null
                ? null : success.getPriceDetails().getReferenceNo();
    }

    public DidaHotel firstHotel() {
        if (success == null || success.getPriceDetails() == null
                || success.getPriceDetails().getHotelList() == null
                || success.getPriceDetails().getHotelList().isEmpty()) {
            return null;
        }
        return success.getPriceDetails().getHotelList().get(0);
    }

    /** 验价回传的那条报价；缺席即该票已死 */
    public DidaRatePlan firstRatePlan() {
        DidaHotel hotel = firstHotel();
        if (hotel == null || hotel.getRatePlanList() == null || hotel.getRatePlanList().isEmpty()) {
            return null;
        }
        return hotel.getRatePlanList().get(0);
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

        @JsonProperty("ReferenceNo")
        private String referenceNo;

        @JsonProperty("CheckInDate")
        private String checkInDate;

        @JsonProperty("CheckOutDate")
        private String checkOutDate;

        @JsonProperty("HotelList")
        private List<DidaHotel> hotelList;
    }
}
