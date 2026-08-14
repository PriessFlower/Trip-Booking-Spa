package com.trip.booking.spa.core.api.aichotels.access;

import com.trip.booking.spa.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.trip.booking.spa.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AvailabilityAccess extends BaseHttpAccess<AvailabilityRequest, AvailabilityResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    private DistributedRateLimiter redisRateLimiter;

    private static int QPS = 30;

    public AvailabilityAccess(String host, String apiClientKey, String Date, String apiClientToken,
                              DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.PRODUCT_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICE, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<AvailabilityResponse> request(String url, AvailabilityRequest request, IParser<AvailabilityResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        long start = System.currentTimeMillis();
        ResponseResult<AvailabilityResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
        log.info("美联查询接口耗时：{}", System.currentTimeMillis() - start);
        if (result.getHttpStatus() == 429 || result.getData().getResult().getReturn_status().getException().equals("Exceeded the allowed QPS")) {
            return null;
        }
        AvailabilityResponse response = result.getData();
        response.setHotelCode(request.getHotel_id() + "");
        result.setData(response);
        return result;
    }

    @Override
    protected void beforeAccess(AvailabilityRequest request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected AvailabilityResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, AvailabilityResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

}
