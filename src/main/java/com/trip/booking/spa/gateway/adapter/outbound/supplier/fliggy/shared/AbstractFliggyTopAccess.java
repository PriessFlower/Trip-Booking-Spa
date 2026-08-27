package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞猪（淘宝 TOP）通道基类：POST form + TOP MD5 签名，请求形态见
 * docs/fliggy/distribution-api.md §1。timestamp 必须显式取 Asia/Shanghai（时区错=签名错）；
 * {@code simplify=true} 与响应模型的简化形态解析必须一致。重试一律 0。
 */
public abstract class AbstractFliggyTopAccess<T extends FliggyTopResponse> extends BaseHttpAccess<FliggyTopCall, T> {

    private static final DateTimeFormatter TOP_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final FliggyProperties properties;

    protected AbstractFliggyTopAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                                      FliggyProperties properties) {
        super(SupplierSourceEnum.FLIGGY, dataType, monitorKey, 0);
        this.properties = properties;
    }

    @Override
    protected ResponseResult<T> request(String url, FliggyTopCall call, IParser<T> parser) throws Exception {
        Map<String, String> params = new LinkedHashMap<>(call.getBizParams());
        params.put("method", call.getMethod());
        params.put("app_key", properties.getAppKey());
        params.put("session", properties.getSession());
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("sign_method", "md5");
        params.put("simplify", "true");
        params.put("timestamp", LocalDateTime.now(BEIJING).format(TOP_TS));
        params.put("sign", FliggySignUtil.sign(params, properties.getSecret()));

        String body = HttpUtils.doPost(url, Map.of("Accept", "application/json"), params);
        return new ResponseResult<>(body, parser.parse(body));
    }

    /** 平台频控进 THROTTLED 档（与业务错误分开计数，F-8.2） */
    @Override
    protected boolean isThrottled(T response) {
        return response != null && response.isPlatformThrottled();
    }

    @Override
    protected String errorCode(T response) {
        return response == null ? null : response.metricErrorCode();
    }

    @Override
    protected void beforeAccess(FliggyTopCall call) {
        // 限流已统一在 BaseHttpAccess.access()，此处无业务前置
    }

    @Override
    protected String buildRequestUrl() {
        return properties.getUrlHost();
    }
}
