package com.bingo.hotel.spa.intl.core.api.fastpay.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.SearchRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

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
    protected SearchResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, SearchResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
