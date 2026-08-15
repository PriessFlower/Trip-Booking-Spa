package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * hotel.detail（查价）响应。外层壳 {@code {Code, Guid, Result}}，Code="0" 为成功；
 * 非 0 时 Code 形如 {@code "H001144|获取促销产品失败-…"}（竖线分隔码与文案）。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongHotelDetailResponse implements BaseResponse {

    private String code;

    private String guid;

    private Result result;

    @Override
    public boolean isSucc() {
        return "0".equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return result == null || CollectionUtils.isEmpty(result.getHotels());
    }

    /** 外层 Code 里竖线前的错误码（成功时即 "0"） */
    public String errorCode() {
        if (code == null) {
            return null;
        }
        int bar = code.indexOf('|');
        return bar < 0 ? code : code.substring(0, bar);
    }

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private Integer count;

        private List<ElongHotel> hotels;
    }

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElongHotel {

        private String hotelId;

        private BigDecimal lowRate;

        private String currencyCode;

        private List<ElongRoom> rooms;
    }

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElongRoom {

        /** 物理房型 id（如 "0022"）；注意房型锚是 RatePlan.RoomTypeId，不是它 */
        private String roomId;

        private String name;

        private String nameEn;

        private List<ElongRatePlan> ratePlans;
    }
}
