package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.order.detail 响应。Status 取值表（官方文档，2026-08-15 核对）：
 * A已确认/B NO-SHOW/B1有预定未查到/B2待查/B3暂不确定/C已结账/D删除/E取消/F已入住/
 * G变价/H变更/N新单/O满房/S特殊/U特殊满房/V已审/Z删除另换酒店。
 * 映射进我方状态码的规则见 ElongOrderQuerySyncServiceImpl。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongOrderDetailResponse implements BaseResponse {

    private String code;

    private String guid;

    private Result result;

    @Override
    public boolean isSucc() {
        return "0".equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return result == null;
    }

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

        private Long orderId;

        /** 订单状态原文（A/B/…/Z），映射不上时原样透出给上游 */
        private String status;

        private BigDecimal totalPrice;

        private String currencyCode;

        private String arrivalDate;

        private String departureDate;

        private Integer numberOfRooms;

        private String cancelTime;

        private Boolean isCancelable;

        /** 此刻取消的客人违约金（元） */
        private BigDecimal penaltyToCustomer;

        private String affiliateConfirmationId;

        private String creationDate;

        /** 房间明细；酒店确认号埋在 OrderRooms[i].RoomConfirmationNumber（cursor 实证） */
        private JsonNode orderRooms;
    }
}
