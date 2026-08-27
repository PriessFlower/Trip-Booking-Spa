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
 * 飞猪（淘宝 TOP）通道基类：POST form-urlencoded + TOP MD5 签名 + 双层信封。
 *
 * <p>请求形态（cursor 生产实现同构 + docs/fliggy/distribution-api.md §1）：
 * {@code POST <urlHost>}，body 为 form 键值对 = 业务参数 + 公共参数
 * （method/app_key/session/format=json/v=2.0/sign_method=md5/simplify=true/timestamp/sign）。
 * timestamp 是<b>北京时间</b> {@code yyyy-MM-dd HH:mm:ss}——显式取 Asia/Shanghai，
 * 不吃 JVM 缺省时区（本地开发机不在东八区时签名必错且报错语焉不详）。
 *
 * <p>{@code simplify=true} 跟随 cursor 生产取值：响应省去 TOP 的包装数组层，
 * 响应模型按简化形态解析，两者必须一致。
 *
 * <p>限流走 BaseHttpAccess 唯一闸门，键 {@code GLOBAL_LIMIT:FLIGGY:<接口>[:<用途>]}。
 * 重试一律 0：TOP 平台错误（含 session 病）重试只会烧配额换同一个错。
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
