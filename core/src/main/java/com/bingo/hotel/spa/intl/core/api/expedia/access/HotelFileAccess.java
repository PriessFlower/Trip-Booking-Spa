package com.bingo.hotel.spa.intl.core.api.expedia.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ExpediaUtils;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.Map;

public class HotelFileAccess extends BaseHttpAccess<HotelInfoRequest, HotelFileResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/v3/files/properties/catalog";

    private static int QPS = 30;

    public HotelFileAccess(String host, String language, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }


    @Override
    protected ResponseResult<HotelFileResponse> request(String url, HotelInfoRequest request, IParser<HotelFileResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("language", language);
        body.put("property_id", request.getProperty_id());
        body.put("supply_source", request.getSupply_source());
        ResponseResult<HotelFileResponse> result = HttpUtils.accessGet(url, headers, body, parser);
        return result;
    }

    public static void main(String[] args) {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", new ExpediaUtils().signGeneration());
        headers.put("Customer-Ip", "5.5.5.5");
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("language", "zh-CN");
        body.put("supply_source", "expedia");

        ResponseResult<HotelFileResponse> result = null;
        try {
            result = HttpUtils.accessGet("https://test.ean.com/v3/files/properties/catalog", headers, body, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(JsonUtils.writeObject2Json(result));
    }

    @Override
    protected void beforeAccess(HotelInfoRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected HotelFileResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelFileResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
