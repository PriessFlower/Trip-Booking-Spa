package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 姓名节点 {@code {"First":名,"Last":姓}}（官方 booking-api/booking-confirm，2026-09-13 查阅）。
 * 请求与响应同形。官方注 2：Name 字段不限语言，但建议转成英文以提高第三方供应商的接受率。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaName {

    @JsonProperty("First")
    private String first;

    @JsonProperty("Last")
    private String last;

    public DidaName() {
    }

    public DidaName(String first, String last) {
        this.first = first;
        this.last = last;
    }
}
