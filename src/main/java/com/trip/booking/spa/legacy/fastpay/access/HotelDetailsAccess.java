package com.trip.booking.spa.legacy.fastpay.access;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.fastpay.bean.request.HotelInfoRequest;
import com.trip.booking.spa.legacy.fastpay.bean.response.HotelDetailResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

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
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
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
