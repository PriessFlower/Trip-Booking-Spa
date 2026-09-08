package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

import java.util.Map;

/**
 * 道旅通道基类：POST JSON，凭据在请求体的 Header 节点里（无签名、无会话）。
 *
 * <p><b>Accept-Encoding: gzip 是硬要求</b>：不带它道旅直接拒答
 * {@code {"Error":{"Code":"-2","Message":"gzip is required, please add Accept-Encoding: gzip
 * in your request header."}}}（2026-09-08 腾讯云生产机实测，文档未写）。Apache HttpClient
 * 默认就会带并自动解压，此处显式声明是为了让这条契约在代码里看得见。
 *
 * <p>限流走 {@code BaseHttpAccess.access()} 唯一闸门，两级键
 * {@code GLOBAL_LIMIT:DIDA:<接口>[:<用途>]}。<b>道旅未公开配额</b>（官方缓存建议页与 FAQ
 * 均无数值，2026-09-08 查阅），取值依据见 {@code config/supplier-capability/dida.yaml}。
 *
 * <p>重试一律 0：业务错误码（如 2006 报价失效）也表现为 {@code isSucc()=false}，重试只会
 * 烧配额换同一个错；网络类失败由上层按 INDETERMINATE 回报，交上游决定是否重试。
 */
public abstract class AbstractDidaJsonAccess<U, T extends BaseResponse> extends BaseHttpAccess<U, T> {

    /** 「超过流量限制（超过QPS限制）」，官方 information-hub/api-error-code，2026-09-08 查阅 */
    private static final String THROTTLE_CODE = "2022";

    private final DidaProperties properties;

    private final String path;

    protected AbstractDidaJsonAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                                     DidaProperties properties, String path) {
        super(SupplierSourceEnum.DIDA, dataType, monitorKey, 0);
        this.properties = properties;
        this.path = path;
    }

    @Override
    protected ResponseResult<T> request(String url, U request, IParser<T> parser) throws Exception {
        Map<String, String> headers = Map.of(
                "Accept", "application/json",
                "Accept-Encoding", "gzip");
        return HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
    }

    /** 供应商侧频控与普通业务错误分开计数（F-8.2），它是调速的唯一直接指标 */
    @Override
    protected boolean isThrottled(T response) {
        return THROTTLE_CODE.equals(errorCode(response));
    }

    @Override
    protected void beforeAccess(U request) {
        // 限流已统一在 BaseHttpAccess.access()，此处无业务前置
    }

    @Override
    protected String buildRequestUrl() {
        return properties.getUrlHost() + path;
    }

    protected DidaProperties properties() {
        return properties;
    }
}
