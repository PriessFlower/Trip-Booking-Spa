package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyTokenRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyTokenResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

import java.util.Map;

/**
 * 取 token 通道（{@code POST /api/v2/Authentication/GetToken}）。
 *
 * <p><b>{@code X-Wid} 请求头在这一步就必须带</b>——2026-09-14 生产四组对照实测：带它就 200
 * （body 字段大小写无所谓），不带一律 HTTP 401 且<b>响应体为空</b>。官方「使用说明」把 X-Wid
 * 写成调用<i>业务</i>接口时才加，照文档写会得到一个没有任何提示的 401。
 *
 * <p>重试 0：401（缺头/凭据被吊销）与 500（凭据错）重试都只会得到同一个错，白烧配额；
 * 网络类失败由上层按不确定回报。
 */
public class ClwyTokenAccess extends BaseHttpAccess<ClwyTokenRequest, ClwyTokenResponse> {

    private static final String PATH = "/api/v2/Authentication/GetToken";

    private final ClwyProperties properties;

    public ClwyTokenAccess(ClwyProperties properties) {
        super(SupplierSourceEnum.CLWY, SupplierDataTypeEnum.AUTH_TOKEN,
                MonitorNameEnum.SPA_SUPPLIER_API_AUTH_TOKEN, 0);
        this.properties = properties;
    }

    @Override
    protected ResponseResult<ClwyTokenResponse> request(String url, ClwyTokenRequest request, IParser<ClwyTokenResponse> parser)
            throws Exception {
        Map<String, String> headers = Map.of(
                "Accept", "application/json",
                // 见类注释：这一步就要带，否则 401 空体
                "X-Wid", properties.getWid(),
                // 只声明 gzip，<b>不要声明 br</b>：官方 2025.06.12 起同时支持 gzip 与 br，而声明了 br
                // 服务端就真的回 br——本仓的 HTTP 客户端（Apache HttpClient）只会透明解 gzip，
                // 拿到 br 就是一堆二进制，解析恒为 null、报文长得像"200 但没内容"。
                // 2026-09-14 实测三档：声明 "gzip, br" 回 br（首字节 0bb0…）、声明 "gzip" 回 gzip
                // （首字节 1f8b…，能解）、不声明则回明文。
                "Accept-Encoding", "gzip");
        return HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
    }

    /** 凭据错时 HTTP 是 200、错在体内，故必须解析错误响应，否则拿不到 message */
    @Override
    public boolean isParseError() {
        return true;
    }

    @Override
    protected String errorCode(ClwyTokenResponse response) {
        return response == null || response.getCode() == null ? null : String.valueOf(response.getCode());
    }

    @Override
    protected void beforeAccess(ClwyTokenRequest request) {
        // 限流已统一在 BaseHttpAccess.access()
    }

    @Override
    protected String buildRequestUrl() {
        return properties.getUrlHost() + PATH;
    }

    @Override
    protected ClwyTokenResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ClwyTokenResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
