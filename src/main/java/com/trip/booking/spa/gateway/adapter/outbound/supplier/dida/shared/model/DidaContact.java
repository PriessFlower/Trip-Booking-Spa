package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 联系人（官方 booking-api/booking-confirm Contact，2026-09-13 查阅）：「可以填客人的联系信息 或
 * 贵司的订单客服联系信息」。节点必填，Phone/Email 未标必填——cursor 生产账号只带 Name 的请求
 * 也成单了（其 DidaTravelTest 留存的 2025-11-28 真实报文，BookingID 15801485798）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DidaContact {

    @JsonProperty("Name")
    private DidaName name;

    @JsonProperty("Phone")
    private String phone;

    @JsonProperty("Email")
    private String email;
}
