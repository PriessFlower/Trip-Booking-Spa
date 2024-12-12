package com.bingo.hotel.spa.intl.core.api.fastpay.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.HotelListRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.HotelListResponse;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
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
