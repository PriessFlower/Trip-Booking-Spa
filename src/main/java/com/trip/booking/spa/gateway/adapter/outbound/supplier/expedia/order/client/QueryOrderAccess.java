package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order.client;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryOrderResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 按我方业务单号反查 Expedia 订单。
 *
 * <p><b>这是下单链路的确证手段，也是本服务最重要的一个接口。</b>下单调用超时或返回
 * 499/5xx 时，无从判断订单是否已在 Expedia 侧成立；此时凭下单时传入的
 * {@code affiliate_reference_id} 反查，即可得到确切答案，从而把「不确定」收敛为
 * 「确实成立」或「确实不存在」。
 *
 * <p>请求：{@code GET /v3/itineraries?affiliate_reference_id={单号}&email={邮箱}}。
 * 邮箱必须与下单时一致，否则 Expedia 不返回结果。
 *
 * <p>本接口是只读的、可安全重试，故与下单不同，允许一次重试以穿过瞬时抖动。
 */
@Slf4j
public class QueryOrderAccess extends BaseHttpAccess<String, QueryOrderResponse> {

    private static final String ITINERARIES_PATH = "/v3/itineraries";

    private final String host;
    private final String affiliateReferenceId;
    private final String email;
    private final String authorization;
    private final String customerIp;
    private final String customerSessionId;

    public QueryOrderAccess(String host, String affiliateReferenceId, String email, String authorization,
                            String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        // 只读接口，允许 1 次重试
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.QUERY_ORDER,
                MonitorNameEnum.SPA_SUPPLIER_API_QUERY_ORDER, 1);
        this.host = host;
        this.affiliateReferenceId = affiliateReferenceId;
        this.email = email;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
    }

    @Override
    protected ResponseResult<QueryOrderResponse> request(String url, String request,
                                                         IParser<QueryOrderResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Accept", "application/json");
        ResponseResult result = HttpUtils.accessGet(url, headers, null, parser);
        log.info("expedia queryOrder affiliateReferenceId:{} response:{}",
                affiliateReferenceId, JsonUtils.writeObject2Json(result));
        return result;
    }

    @Override
    protected void beforeAccess(String request) {
        // 限流由 BaseHttpAccess.access() 统一处理
    }

    /**
     * 两个参数都必须 URL 编码。上游单号的字符集由上游决定、不受本服务约束；一旦其中出现
     * {@code &}、{@code =}、空格等字符，裸拼接会产出非法 URL，该单便<b>永远</b>查不出来——
     * 每次都落 INDETERMINATE，那笔订单从此无法确证。这正是三态契约要消除的死角，
     * 故此处编码不是防御性冗余，而是契约的一部分。
     */
    @Override
    protected String buildRequestUrl() {
        return host + ITINERARIES_PATH
                + "?affiliate_reference_id=" + urlEncode(affiliateReferenceId)
                + "&email=" + urlEncode(email);
    }

    static String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 成功响应是 JSON 数组（可能为空数组，即查无此单）。为统一承载，包成对象后解析。
     */
    @Override
    protected QueryOrderResponse parseResponse(String data) {
        try {
            return QueryOrderResponse.of(data);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /** 错误体同样需要解析，否则无法区分「查无此单」与「查单本身失败」 */
    @Override
    public boolean isParseError() {
        return true;
    }
}
