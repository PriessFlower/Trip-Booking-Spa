package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 艺龙 REST 网关通道基类：六参 GET + 双层 MD5 签名。
 *
 * <p>请求形态（cursor 实现 + docs/elong/hotel.data.validate.json 抓包实证）：
 * {@code GET <urlHost>?user=&method=&timestamp=&format=json&data=<URLEncode(JSON)>&signature=}。
 * timestamp 为 Unix 秒，与签名入参同一值，故在发出的瞬间生成。data 以 JSON 原文签名、
 * 由 HttpUtils.buildUrl 在拼 query 时编码一次——顺序不能颠倒。
 *
 * <p>限流走 BaseHttpAccess 唯一闸门，两级键 {@code GLOBAL_LIMIT:ELONG:<接口>[:<用途>]}。
 * <b>艺龙按方法各自限，没有账号总额</b>——2026-08-24 核对开放平台「接口能力」页：
 * {@code hotel.detail} 15、{@code hotel.data.validate} 10、{@code hotel.order.detail} 50、
 * 下单与取消各 3。此前注释写的"全账号硬额度 10、各接口之和不得超过它"是错的，那个虚构的
 * 总额把查价长期锁在能力的 40%。真正要守的不变式是「同一接口的各用途桶之和 ≤ 该接口桶」
 * （Nacos ratelimit.qps，样例见
 * config/nacos/trip-booking-spa.yaml.example）。
 *
 * <p>重试一律 0：业务错误码（如 H001083）也表现为 isSucc()=false，重试只会烧配额
 * 换同一个错误；网络类失败由上层按 INDETERMINATE 回报，交上游决定是否重试。
 */
public abstract class AbstractElongRestAccess<T extends BaseResponse> extends BaseHttpAccess<ElongRestCall, T> {

    private final ElongProperties properties;

    protected AbstractElongRestAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                                      ElongProperties properties) {
        super(SupplierSourceEnum.ELONG, dataType, monitorKey, 0);
        this.properties = properties;
    }

    @Override
    protected ResponseResult<T> request(String url, ElongRestCall call, IParser<T> parser) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        // 参数序保持 user/method/timestamp/format/data/signature（艺龙示例序，虽 query 序不参与签名）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user", properties.getUser());
        params.put("method", call.getMethod());
        params.put("timestamp", timestamp);
        params.put("format", "json");
        params.put("data", call.getDataJson());
        params.put("signature", ElongSignUtil.sign(timestamp, call.getDataJson(),
                properties.getAppKey(), properties.getSecret()));

        Map<String, String> headers = Map.of("Accept", "application/json");
        String body = HttpUtils.doGet(url, headers, params);
        return new ResponseResult<>(body, parser.parse(body));
    }

    @Override
    protected void beforeAccess(ElongRestCall call) {
        // 限流已统一在 BaseHttpAccess.access()，此处无业务前置
    }

    @Override
    protected String buildRequestUrl() {
        return properties.getUrlHost();
    }
}
