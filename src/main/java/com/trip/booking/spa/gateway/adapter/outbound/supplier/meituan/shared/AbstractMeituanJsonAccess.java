package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 美团业务通道基类：POST JSON 到<b>同一个 URL</b>，调哪个接口由体内的 {@code method} 决定。
 *
 * <p>鉴权没有请求头，凭据在体内（accesskey + signature），每请求现签一次，故本类没有
 * 401 续期那套东西——与差旅无忧正相反。
 *
 * <p>业务重试一律 0：本家的失败都带明确业务码（如 3=房态不满足、6=库存不足），再打一遍
 * 只会换回同一个答案。真正的网络抖动由基类的 HTTP 层重试兜。
 */
@Slf4j
public abstract class AbstractMeituanJsonAccess<U, T extends BaseResponse> extends BaseHttpAccess<U, T> {

    private final MeituanProperties properties;

    private final String method;

    protected AbstractMeituanJsonAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                                        MeituanProperties properties, String method) {
        super(SupplierSourceEnum.MEITUAN, dataType, monitorKey, 0);
        this.properties = properties;
        this.method = method;
    }

    @Override
    protected ResponseResult<T> request(String url, U request, IParser<T> parser) throws Exception {
        String dataJson = request == null ? null : JsonUtils.writeObject2Json(request);
        String body = JsonUtils.writeObject2Json(MeituanSigner.build(method, dataJson, properties));
        return HttpUtils.access(url, headers(), body, parser);
    }

    private Map<String, String> headers() {
        return Map.of(
                "Content-Type", "application/json;charset=UTF-8",
                // 报价档是整店全产品（实测单店单住期 297~321 条产品），不压缩是纯浪费；
                // 服务端按 Content-Encoding 如实标注，Apache HttpClient 透明解 gzip
                "Accept-Encoding", "gzip");
    }

    /** 失败是 HTTP 200 + 体内 code != 0，错在体内，故必须解析错误响应 */
    @Override
    public boolean isParseError() {
        return true;
    }

    @Override
    protected void beforeAccess(U request) {
        // 限流已统一在 BaseHttpAccess.access()
    }

    /** 本家所有接口共用一个地址，接口名在体内 */
    @Override
    protected String buildRequestUrl() {
        return properties.getUrl();
    }

    protected MeituanProperties properties() {
        return properties;
    }
}
