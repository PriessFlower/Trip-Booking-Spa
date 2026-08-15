package com.trip.booking.spa.legacy.fastpay.access;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.fastpay.bean.request.SearchRequest;
import com.trip.booking.spa.legacy.fastpay.bean.response.SearchResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class QueryProductAccess extends BaseHttpAccess<SearchRequest, SearchResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/booking/search";

    private static int QPS = 100;

    public QueryProductAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.FASTPAYHOTELS, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
        this.host = host;
        this.authorization = authorization;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<SearchResponse> request(String url, SearchRequest request, IParser<SearchResponse> parser) throws Exception {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json");
        Map<String, Object> body = Maps.newHashMap();
        body.put("messageID", request.getMessageID());
        body.put("currency", "USD");
        body.put("checkIn", request.getCheckIn());
        body.put("checkout", request.getCheckOut());
        body.put("occupancies", request.getOccupancies());
        body.put("hotelCodes", request.getHotelCodes());
        String result = HttpUtils.doPostObject(url, body, headers);
        SearchResponse searchResponse = JsonUtils.readValue(result, SearchResponse.class);
        return new ResponseResult<>(searchResponse);
    }

    @Override
    protected void beforeAccess(SearchRequest request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected SearchResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, SearchResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
