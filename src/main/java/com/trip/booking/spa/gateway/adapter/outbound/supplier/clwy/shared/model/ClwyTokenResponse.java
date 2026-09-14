package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 取 token 响应（官方 01-authentication）：{@code {"code":200,"token":"...","message":""}}。
 *
 * <p><b>两种失败长得完全不一样</b>（2026-09-14 生产实测）：
 * <ul>
 *   <li><b>缺 {@code X-Wid} 请求头</b> → HTTP 401，<b>响应体为空</b>，没有任何 code/message。
 *       官方「使用说明」把 X-Wid 写成调用<i>业务</i>接口才要，实际取 token 这一步就要</li>
 *   <li><b>凭据错</b> → HTTP 200，体内
 *       {@code {"token":null,"code":500,"message":"Invalid parameter:Wid or ApiKey."}}</li>
 * </ul>
 * 故判成功必须同时看 code 与 token 非空，不能只看 HTTP 状态。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClwyTokenResponse implements BaseResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("token")
    private String token;

    @JsonProperty("message")
    private String message;

    @Override
    public boolean isSucc() {
        return Integer.valueOf(ClwyCodes.OK).equals(code) && StringUtils.isNotBlank(token);
    }

    /** 令牌接口没有「空结果」这一档：拿不到 token 就是失败 */
    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
