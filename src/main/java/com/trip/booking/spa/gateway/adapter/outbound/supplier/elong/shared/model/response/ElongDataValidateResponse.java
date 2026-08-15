package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.data.validate（验价）响应。外层壳同 hotel.detail；业务失败时错误码在外层
 * Code（如 H001083），内层码（7010/7015）嵌在文案里——分类见适配层，此处只保真。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongDataValidateResponse implements BaseResponse {

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

        /** OK 才是验价通过；其余取值（Product/Inventory/Rate）码义未经官方文档核实，只透传 */
        private String resultCode;

        private String errorMessage;

        /** 免费取消截止（带 +08:00 偏移） */
        private String freeCancelTime;

        private String cancelTime;

        private BigDecimal penaltyAmount;

        private String currencyCode;

        /**
         * 国际验价详情（真实退改与验后价的权威来源）：
         * {@code ratePlanInfo.RateNightlyRateList[{Rate,MinRate,Date}]}、
         * {@code ratePlanInfo.CancelPolicyList[{Penalty,PenaltyRMB,DateFrom,DateTo}]}。
         * JSON 键为小驼峰 {@code interValidateInfo}，需显式映射。
         */
        @JsonProperty("interValidateInfo")
        private JsonNode interValidateInfo;
    }
}
