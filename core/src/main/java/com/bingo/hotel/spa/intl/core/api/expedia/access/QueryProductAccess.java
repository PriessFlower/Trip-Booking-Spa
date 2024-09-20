package com.bingo.hotel.spa.intl.core.api.expedia.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.QueryPriceRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

import java.util.List;
import java.util.Map;

@Slf4j
public class QueryProductAccess extends BaseHttpAccess<QueryPriceRequest, QueryPriceResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/properties/availability";

    private static int QPS = 500;

    public QueryProductAccess(String host, String language, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<QueryPriceResponse> request(String url, QueryPriceRequest request, IParser<QueryPriceResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("property_id", request.getProperty_id());
        body.put("language", language);
        body.put("checkin", request.getCheckin());
        body.put("checkout", request.getCheckout());
        body.put("country_code", "CN");
        body.put("currency", request.getCurrency());
        StringBuilder occupancyStr = new StringBuilder();
        request.getOccupancies().forEach(occupancy -> {
            occupancyStr.append("occupancy=").append(occupancy);
        });
        body.put("", occupancyStr.toString());
        body.put("rate_plan_count", "250");
        body.put("sales_channel", "agent_tool");
        body.put("sales_environment", request.getSales_environment());
        body.put("billing_terms", request.getBilling_terms());
        body.put("payment_terms", request.getPayment_terms());
        body.put("partner_point_of_sale", request.getPartner_point_of_sale());
        String result = HttpUtils.doGet(url, headers, body);
        List<QueryPriceResponse.HotelPrice> hotelPrices = JsonUtils.decodeJson(result, new TypeReference<List<QueryPriceResponse.HotelPrice>>() {
        });
        QueryPriceResponse queryPriceResponse = new QueryPriceResponse();
        queryPriceResponse.setHotelPrices(hotelPrices);
        return new ResponseResult<>(queryPriceResponse);
    }

    @Override
    protected void beforeAccess(QueryPriceRequest request) {
        if (!redisRateLimiter.tryAcquire(buildGlobalLimitKey(), QPS, RateIntervalUnit.SECONDS, WINDOW_IN_SECONDS, 5)) {
            log.info("expedia接口请求超过限制，每秒请求超过{}次", QPS);
            throw new RedisLimitException("Request exceeds limit key = " + buildGlobalLimitKey()
                    + "request = " + JsonUtils.writeObject2Json(request));
        }
    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected QueryPriceResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, QueryPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
