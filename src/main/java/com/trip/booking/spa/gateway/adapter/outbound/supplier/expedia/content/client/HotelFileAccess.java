package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.request.HotelInfoRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.HotelFileResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
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
