package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyCodes;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.Map;

/**
 * 差旅无忧业务通道基类：POST JSON，鉴权走 {@code Authorization: Bearer <token>} + {@code X-Wid}
 * 两个请求头（官方 01-authentication，2026-09-14 查阅）。
 *
 * <p><b>令牌自续就落在这里</b>（腐性申报 {@code CredentialRenewal.SELF_RENEWING}）：业务调用
 * 收到 HTTP 401 时，作废本地令牌、重取一次、原样重发一次。只重发一次——若新令牌仍被拒，
 * 那就不是"令牌过期"而是凭据本身的问题，再试只是烧配额。
 *
 * <p><b>这次重发不计入 {@code retries}</b>：基类的重试是"同一个请求再打一遍"，语义是网络抖动；
 * 令牌续期是"换了凭据的另一个请求"，混在一起会让重试指标读不出真正的抖动率。
 *
 * <p>业务重试一律 0：本家失败只有 {@code code=500} 一种形态且文案唯一（{@code No Availability}），
 * 重试只会换回同一个答案。
 *
 * <p>请求头只声明 {@code gzip}，<b>不声明 br</b>——理由见 {@code headers()} 内注释。报价响应是
 * 整店全房型全价格计划（实测单店单住期 230 条报价），压缩仍然要开。
 */
@Slf4j
public abstract class AbstractClwyJsonAccess<U, T extends BaseResponse> extends BaseHttpAccess<U, T> {

    private final ClwyProperties properties;

    private final ClwyTokenProvider tokenProvider;

    private final String path;

    /**
     * 本次调用的用途，供令牌续期透传。
     *
     * <p>每次调用都 new 一个 Access 实例（各 client 的工厂即如此），故实例字段是安全的；
     * 之所以要存，是因为 {@code BaseHttpAccess.request()} 的签名里没有用途——而续期发生在
     * 它内部。
     */
    private volatile CallPurpose purpose = CallPurpose.LIVE;

    protected AbstractClwyJsonAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                                     ClwyProperties properties, ClwyTokenProvider tokenProvider, String path) {
        super(SupplierSourceEnum.CLWY, dataType, monitorKey, 0);
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.path = path;
    }

    @Override
    public ResponseResult<T> access(U request, CallPurpose purpose) {
        this.purpose = purpose;
        return super.access(request, purpose);
    }

    @Override
    protected ResponseResult<T> request(String url, U request, IParser<T> parser) throws Exception {
        String token = tokenProvider.token(purpose);
        if (token == null) {
            // 拿不到令牌 = 没问出结果，不是没货。返回 401 让上层落"不确定"，别让它长得像业务失败
            log.error("差旅无忧调用未取得令牌，本次调用未发出,interface={}", path);
            return new ResponseResult<>(HttpStatus.SC_UNAUTHORIZED, null);
        }
        String body = JsonUtils.writeObject2Json(request);
        ResponseResult<T> result = HttpUtils.access(url, headers(token), body, parser);
        if (result == null || result.getHttpStatus() != HttpStatus.SC_UNAUTHORIZED) {
            return result;
        }
        // 401：服务端认为令牌不可用。本地到期时间只是推算，可能落后于事实——作废重取，只重发一次
        log.warn("差旅无忧调用返回 401，作废令牌后重试一次,interface={}", path);
        tokenProvider.invalidate();
        String renewed = tokenProvider.token(purpose);
        if (renewed == null) {
            return result;
        }
        return HttpUtils.access(url, headers(renewed), body, parser);
    }

    private Map<String, String> headers(String token) {
        return Map.of(
                "Accept", "application/json",
                "Authorization", "Bearer " + token,
                "X-Wid", properties.getWid(),
                // 只声明 gzip，<b>不要声明 br</b>：官方 2025.06.12 起同时支持 gzip 与 br，而声明了 br
                // 服务端就真的回 br——本仓的 HTTP 客户端（Apache HttpClient）只会透明解 gzip，
                // 拿到 br 就是一堆二进制，解析恒为 null、报文长得像"200 但没内容"。
                // 2026-09-14 实测三档：声明 "gzip, br" 回 br（首字节 0bb0…）、声明 "gzip" 回 gzip
                // （首字节 1f8b…，能解）。<b>业务端点不许不声明</b>：GetPrice 少这个头直接回
                // 415「Please use gzip or br encoding in Accept-Encoding header」（取令牌那个
                // 端点不挑，不声明照回明文）。故这个头是必需项，不是优化项。
                "Accept-Encoding", "gzip");
    }

    /** 失败是 HTTP 200 + code 500，错在体内，故必须解析错误响应 */
    @Override
    public boolean isParseError() {
        return true;
    }

    @Override
    protected void beforeAccess(U request) {
        // 限流已统一在 BaseHttpAccess.access()
    }

    @Override
    protected String buildRequestUrl() {
        return properties.getUrlHost() + path;
    }

    protected ClwyProperties properties() {
        return properties;
    }

    /** 本家没有频控专用码：官方只有 200/500 两个值，撞限也表现为 500。故不覆写 isThrottled */
    protected static boolean isNoAvailability(String message) {
        return ClwyCodes.NO_AVAILABILITY.equalsIgnoreCase(message == null ? null : message.trim());
    }
}
