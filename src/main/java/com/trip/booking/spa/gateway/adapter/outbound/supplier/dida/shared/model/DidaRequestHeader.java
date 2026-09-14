package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 机构账号信息。道旅<b>每个请求体自带凭据</b>，无会话、无令牌、无到期
 * （腐性申报 CredentialRenewal.STATELESS）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaRequestHeader {

    @JsonProperty("ClientID")
    private String clientId;

    @JsonProperty("LicenseKey")
    private String licenseKey;

    public DidaRequestHeader(String clientId, String licenseKey) {
        this.clientId = clientId;
        this.licenseKey = licenseKey;
    }
}
