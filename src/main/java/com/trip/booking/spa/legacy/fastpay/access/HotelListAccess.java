package com.trip.booking.spa.legacy.fastpay.access;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.fastpay.bean.request.HotelListRequest;
import com.trip.booking.spa.legacy.fastpay.bean.response.HotelListResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.Map;

public class HotelListAccess extends BaseHttpAccess<HotelListRequest, HotelListResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/hotel/list";

    private static int QPS = 30;

    public HotelListAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.FASTPAYHOTELS, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.authorization = authorization;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<HotelListResponse> request(String url, HotelListRequest request, IParser<HotelListResponse> parser) throws Exception {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json");
        Map<String, Object> body = Maps.newHashMap();
        body.put("messageID", request.getMessageID());
        body.put("fromLastUpdateDate", request.getFromLastUpdateDate());
        body.put("toLastUpdateDate", request.getToLastUpdateDate());
        String hotelList = HttpUtils.doPostObject(url, body, headers);
        HotelListResponse hotelListResponse = JsonUtils.readValue(hotelList, HotelListResponse.class);
        return new ResponseResult<>(hotelListResponse);
    }

    @Override
    protected void beforeAccess(HotelListRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected HotelListResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelListResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
