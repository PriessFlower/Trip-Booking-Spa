package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 取 token 请求体（官方 01-authentication，2026-09-14 查阅）。
 *
 * <p>字段名按官方参数表用大写开头的 {@code Wid}/{@code ApiKey}。<b>2026-09-14 实测：服务端对
 * 这两个字段名大小写不敏感</b>（小写 {@code wid}/{@code apiKey} 同样返回 200，官方"接口示例"
 * 里给的正是小写）——但仓内照参数表写，不跟示例走。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClwyTokenRequest {

    @JsonProperty("Wid")
    private String wid;

    @JsonProperty("ApiKey")
    private String apiKey;

    public ClwyTokenRequest(String wid, String apiKey) {
        this.wid = wid;
        this.apiKey = apiKey;
    }
}
