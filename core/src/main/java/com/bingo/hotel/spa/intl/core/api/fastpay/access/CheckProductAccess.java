package com.bingo.hotel.spa.intl.core.api.fastpay.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.CheckPriceRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

import java.util.Map;

@Slf4j
public class CheckProductAccess extends BaseHttpAccess<CheckPriceRequest, CheckPriceResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/booking/livecheck";

    private static int QPS = 100;

    public CheckProductAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
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
        body.put("messageID", request.getMessageID());
        body.put("currency", "USD");
        body.put("checkIn", request.getCheckIn());
        body.put("checkOut", request.getCheckOut());
        body.put("occupancy", request.getOccupancy());
        body.put("hotelCode", request.getHotelCode());
        body.put("productCode",request.getProductCode());
        body.put("quantity",request.getQuantity());
        String result = HttpUtils.doPostObject(url, body, headers);
        CheckPriceResponse searchResponse = JsonUtils.readValue(result, CheckPriceResponse.class);
        return new ResponseResult<>(searchResponse);
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
