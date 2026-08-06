package com.trip.booking.spa.core.api.expedia.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.expedia.bean.request.RegionsRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.RegionsInfoResponse;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class RegionAccess extends BaseHttpAccess<RegionsRequest, RegionsInfoResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private String regionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/v3/regions";

    private static int QPS = 30;

    public RegionAccess(String host, String language, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_COUNTRY, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }


    @Override
    protected ResponseResult<RegionsInfoResponse> request(String url, RegionsRequest request, IParser<RegionsInfoResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("language", language);
        body.put("include", request.getInclude());
        body.put("ancestor_id", request.getAncestor_id());
        String resultStr = HttpUtils.doGet(url, headers, body);
        List<RegionsInfoResponse.HotelId> hotelIds = JsonUtils.decodeJson(resultStr, new TypeReference<>() {
        });
        RegionsInfoResponse regionsInfoResponse = new RegionsInfoResponse();
        regionsInfoResponse.setHotelIds(hotelIds);
        return new ResponseResult<>(regionsInfoResponse);
    }

    @Override
    protected void beforeAccess(RegionsRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected RegionsInfoResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, RegionsInfoResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    public static void main(String[] args) {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", new ExpediaUtils().signGeneration());
        headers.put("Customer-Ip", "5.5.5.5");
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("language", "en-US");
        body.put("include", "details");
        body.put("ancestor_id", "237");


        List<RegionsInfoResponse.HotelId> result = null;
        try {
            String resultStr = HttpUtils.doGet("https://test.ean.com/v3/regions", headers, body);
            List<RegionsInfoResponse.HotelId> hotelIds = JsonUtils.decodeJson(resultStr, new TypeReference<List<RegionsInfoResponse.HotelId>>() {
            });
            System.out.println(JsonUtils.writeObject2Json(hotelIds));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
