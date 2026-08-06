package com.trip.booking.spa.core.api.fastpay.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.fastpay.bean.request.HotelInfoRequest;
import com.trip.booking.spa.core.api.fastpay.bean.response.HotelDetailResponse;
import com.trip.booking.spa.core.exception.RedisLimitException;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

import java.util.Map;

@Slf4j
public class HotelDetailsAccess extends BaseHttpAccess<HotelInfoRequest, HotelDetailResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/hotel/details";

    private static int QPS = 10;

    public HotelDetailsAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.FASTPAYHOTELS, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.authorization = authorization;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<HotelDetailResponse> request(String url, HotelInfoRequest request, IParser<HotelDetailResponse> parser) throws Exception {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json");
        Map<String, Object> body = Maps.newHashMap();
        body.put("messageID", request.getMessageID());
        body.put("code", request.getCode());
        body.put("languages", request.getLanguages());
        String hotelDetail = HttpUtils.doPostObject(url, body, headers);
        HotelDetailResponse hotelDetailResponse = JsonUtils.readValue(hotelDetail, HotelDetailResponse.class);
        return new ResponseResult<>(hotelDetailResponse);
    }

    @Override
    protected void beforeAccess(HotelInfoRequest request) {
        if (!redisRateLimiter.tryAcquire(buildGlobalLimitKey(), QPS, RateIntervalUnit.SECONDS, WINDOW_IN_SECONDS, 5)) {
            try {
                log.info("fastpay接口请求超过限制，每秒请求超过{}次", QPS);
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RedisLimitException("Request exceeds limit key = " + buildGlobalLimitKey()
                        + "request = " + JsonUtils.writeObject2Json(request));
            }
        }
    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected HotelDetailResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelDetailResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

//    public static void main(String[] args) {
//        Map<String, String> headers = Maps.newHashMap();
//        headers.put("Authorization", new ExpediaUtils().signGeneration());
//        headers.put("Customer-Ip", "5.5.5.5");
//        headers.put("Content-Type", "application/json");
//        Map<String, String> body = Maps.newHashMap();
//        body.put("language", "en-US");
//        body.put("include", "details");
//
//        ResponseResult<HotelDetailResponse> result = null;
//        try {
//            result = HttpUtils.accessGet("https://test.ean.com/v3/regions/11700", headers, body, null);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println(JsonUtils.writeObject2Json(result));
//    }
}
