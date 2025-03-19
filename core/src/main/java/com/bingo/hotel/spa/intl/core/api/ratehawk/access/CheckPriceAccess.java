package com.bingo.hotel.spa.intl.core.api.ratehawk.access;


import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.CheckPriceRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.BaseResult;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.QueryProductResponse;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

import java.util.Map;

/**
 * 查询产品信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/09
 * @since : 1.0
 **/

@Slf4j
public class CheckPriceAccess extends BaseHttpAccess<CheckPriceRequest, CheckPriceResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/search/lookuprate/";

    private static int QPS = 500;

    public CheckPriceAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.RATEHAWK, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
        this.host = host;
        this.authorization = authorization;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<CheckPriceResponse> request(String url, CheckPriceRequest request, IParser<CheckPriceResponse> parser) throws Exception {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json");
        Map<String, Object> body = Maps.newHashMap();
        body.put("book_hash", request.getBook_hash());
        body.put("language", request.getLanguage());
        String result = HttpUtils.doPostObject(url, body, headers);
        log.info("ratehawk checkprice request:{} response: {}", JsonUtils.writeObject2Json(request), result);
        BaseResult<CheckPriceResponse> checkPriceResponse = JsonUtils.decodeJson(result, new TypeReference<>() {
        });
        if (null == checkPriceResponse || null == checkPriceResponse.getData() || "error".equals(checkPriceResponse.getError())) {
            log.info("ratehawk验价异常 request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
            return null;
        }
        return new ResponseResult<>(checkPriceResponse.getData());
    }

    @Override
    protected void beforeAccess(CheckPriceRequest request) {
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
    protected CheckPriceResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, CheckPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
