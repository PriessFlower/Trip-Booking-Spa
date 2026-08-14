package com.trip.booking.spa.core.api.expedia.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.expedia.bean.response.CreateOrderResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Expedia Rapid 下单。
 *
 * <p><b>请求地址</b>：构造入参 {@code bookHref} 为验价响应中的 {@code links.book.href}
 * （形如 {@code /v3/itineraries?token=...}），整串拼在 host 之后，不另拼路径。
 *
 * <p><b>会话一致性</b>：Rapid 要求验价与下单使用同一 {@code Customer-Ip} 与
 * {@code Customer-Session-Id}。故这两个值必须由调用方从验价环节原样传入，
 * 不得在此另行生成或写死——否则 Expedia 侧视作另一个会话，可能拒单或行为异常。
 *
 * <p><b>禁止重试</b>：本类构造时把重试次数固定为 0。下单不是幂等操作，
 * 传输层重试会在响应丢失时造成重复下单。结果不确定应交由上游查单确证，
 * 而不是在此盲目重发。
 */
@Slf4j
public class CreateOrderAccess extends BaseHttpAccess<String, CreateOrderResponse> {

    private final String host;
    private final String bookHref;
    private final String language;
    private final String authorization;
    private final String customerIp;
    private final String customerSessionId;

    public CreateOrderAccess(String host, String bookHref, String language, String authorization,
                             String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        // 末位 0 = 不重试，理由见类注释
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.CREATE_ORDER,
                MonitorNameEnum.SPA_SUPPLIER_API_CREATE_ORDER, 0);
        this.host = host;
        this.bookHref = bookHref;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
    }

    @Override
    protected ResponseResult<CreateOrderResponse> request(String url, String requestBody,
                                                          IParser<CreateOrderResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        if (language != null && !language.isBlank()) {
            headers.put("Accept-Language", language);
        }
        ResponseResult result = HttpUtils.access(url, headers, requestBody, parser);
        // EAC 模式下请求体不含卡信息，但仍含旅客姓名，故不整体打印
        log.info("expedia createOrder url:{} response:{}", url, JsonUtils.writeObject2Json(result));
        return result;
    }

    @Override
    protected void beforeAccess(String request) {
        // 限流由 BaseHttpAccess.access() 统一处理
    }

    @Override
    protected String buildRequestUrl() {
        return host + bookHref;
    }

    @Override
    protected CreateOrderResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, CreateOrderResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /**
     * 业务错误体（含 type/message）也需解析，否则拒单原因会丢失，
     * 无法据此判定「确定失败」与「结果不确定」。
     */
    @Override
    public boolean isParseError() {
        return true;
    }
}
