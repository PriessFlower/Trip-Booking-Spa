package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 艺龙 data 参数的外层信封：{@code {"Version","Local","Request":{...}}}。
 * JSON 字段一律 PascalCase（艺龙约定），序列化后整串作为 query 参数 {@code data}
 * 参与签名（原文）与 URL 编码（通道层一次）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElongRequestEnvelope {

    private String version;

    private String local = "zh_CN";

    private Object request;

    public ElongRequestEnvelope(String version, Object request) {
        this.version = version;
        this.request = request;
    }
}
