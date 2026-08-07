package com.trip.booking.spa.core.api.expedia.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.CheckPriceResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class CheckPriceAccess extends BaseHttpAccess<String, CheckPriceResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private DistributedRateLimiter redisRateLimiter;


    private static int QPS = 50;


    public CheckPriceAccess(String host, String language, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.CHECK_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }


    @Override
    protected ResponseResult<CheckPriceResponse> request(String url, String request, IParser<CheckPriceResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Content-Type", "application/json");
        ResponseResult result = HttpUtils.accessGet(url + request, headers, null, parser);
        log.info("expedia checkprice request:{} response: {}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
        return result;
    }

    public static void main(String[] args) {
        String str = "{\"status\":\"available\",\"occupancy_pricing\":{\"2\":{\"nightly\":[[{\"type\":\"base_rate\",\"value\":\"1.88\",\"currency\":\"USD\"},{\"type\":\"property_fee\",\"value\":\"0.05\",\"currency\":\"USD\"},{\"type\":\"tax_and_service_fee\",\"value\":\"0.17\",\"currency\":\"USD\"},{\"type\":\"extra_person_fee\",\"value\":\"0.14\",\"currency\":\"USD\"}]],\"stay\":[{\"type\":\"property_fee\",\"value\":\"0.04\",\"currency\":\"USD\"},{\"type\":\"tax_and_service_fee\",\"value\":\"0.06\",\"currency\":\"USD\"}],\"fees\":{\"mandatory_fee\":{\"billable_currency\":{\"value\":\"0.00\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"0.00\",\"currency\":\"USD\"}},\"mandatory_tax\":{\"billable_currency\":{\"value\":\"0.34\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"0.01\",\"currency\":\"USD\"}},\"resort_fee\":{\"billable_currency\":{\"value\":\"34.58\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"1.03\",\"currency\":\"USD\"}}},\"totals\":{\"property_fees\":{\"billable_currency\":{\"value\":\"34.92\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"1.04\",\"currency\":\"USD\"}},\"property_inclusive_strikethrough\":{\"billable_currency\":{\"value\":\"208.17\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"6.20\",\"currency\":\"USD\"}},\"inclusive_strikethrough\":{\"billable_currency\":{\"value\":\"5.16\",\"currency\":\"USD\"},\"request_currency\":{\"value\":\"5.16\",\"currency\":\"USD\"}},\"inclusive\":{\"billable_currency\":{\"value\":\"2.34\",\"currency\":\"USD\"},\"request_currency\":{\"value\":\"2.34\",\"currency\":\"USD\"}},\"strikethrough\":{\"billable_currency\":{\"value\":\"4.86\",\"currency\":\"USD\"},\"request_currency\":{\"value\":\"4.86\",\"currency\":\"USD\"}},\"property_inclusive\":{\"billable_currency\":{\"value\":\"113.49\",\"currency\":\"THB\"},\"request_currency\":{\"value\":\"3.38\",\"currency\":\"USD\"}},\"exclusive\":{\"billable_currency\":{\"value\":\"2.02\",\"currency\":\"USD\"},\"request_currency\":{\"value\":\"2.02\",\"currency\":\"USD\"}}}}},\"links\":{\"book\":{\"method\":\"POST\",\"href\":\"/v3/itineraries?token=QldfCGlcUA4FV1FWBwIFVwxDRwdWXBBLDwheZkFdBwUEAgcEDABSVBldDFQFSwcFWlAYBQBXW0wGXAdRAQQAAlYDVQoQXlVHVApVC0BrDlBnQ1BWWxYBD3w9YHNxcSceQBZfUFI-UAtZWgMEUAVUAFUVUENDVk0AbApXCwZRBVcLBwEKCRNADUNdORZNEVUOWAZcXxZnFVcOWUddVwUWQlRbURxbB0RZVBNaA0BaWlU-VlQHRFoDDVZBAwJUCElRHnQsERdZTEoCUQRBVRZcXRRAEFxbVB4AXFFTDxRGBEcAPUYdFQANMjd5LXV1N3VFVlFMO0VSQAZHW1MECkQDHgAFRF0HDwRbAlIXBVxcBl4IX1wKVQUCFQYCSFFbEUgFQhFZBkZtQBBaB1FVUTpaVFwHAgRQVwQeC1MLW1RLWEBEEV0SARZCQgZeBkEJNGchRF4HEw5WRlpWA2ZbCgVeD11dX1IeWwsDVF9cE0BZAAFQVUtUAU9VU0MEB1NoCwYPAw8GUgcTQENeAFxXBgYAQ0sNWhJAUAxeORZYTV0JM3kleid0cB8GD1sHUjtZA15UXBdqCwpZGj5VXANYQVtQU1hFUhVXXl9AWRYJVxoHVRBKVFNQRhYMXF46Q1lASgVdDRZXQ14FHhNKXQcEGVtaBFoNEwZERE0KXgZBaUcDQBdQWV5sWVEOBVVbAgdSBhZAXBJKWA1WPlEFCAJWBV8GUAdTHwRQAFEZAVACABlRVgoEHVcDVgIGBQRSW11SDRVJQVgSUhZCQGcIUAQCBVECDA4FVhNfVF0GRlVEAVZrEVoIBw9WVVcETFIITgENMQBSHQd5VQIWByIHVRtRVgdXD1dTAQ05FEYCWwNCOVFXE1wTXg9VAFlCBV9dEQYOaEgFUw5WBFEURBBUF11VaxVGQhFeQFJZKXZxNWM0fBdaCkFYEEpNPQBfVAYNJnYSA1EBPVcDEgZBW0NMDVZWX1UVUwgUJ1cdeiBDdgQWIwFBCnVHWFNAdFZDISRDIHYSJ1cXcnkVXXcQcQYSXVNHDHUVVwhUEFAAHCcMQyR6HHQERn1TFF53EABRRnFQHCZ1Q3F3HCAAQVxzRllyHCcBFyQHFwwEEnYDFnJ0Elp1Q1ZRBWlbXltTXQMQQFgRWVlbBUMNckEBcRJTARdBRAFEBgoXUnhDB1dlQFALcQZXEwZUFlZ6EwIBQ1xJARIKVEcHIBUBCzVOWAwdUwpEByIXBlQSR1RbRgwSTEcGB0QCJwUWA3ZEAnJHVXRHUQNAGxVUEAELFgQjElYEe00PX3tWUEMHCxwCIRAKA0AKTlEVVgARVnJAUAAwEgxeRFEKRgN3QANRSUFZCkNaQBoRVAdAVXZXHVIgEQEnFEYWRxZdD1FLOlwFDFMMQ3ReXVRZFhYPCglRCVQGVgMLCFQ=\"}}}";
        CheckPriceResponse checkPriceResponse = JsonUtils.readValue(str, CheckPriceResponse.class);
        System.out.println(JsonUtils.writeObject2Json(checkPriceResponse));
    }

    @Override
    protected void beforeAccess(String request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }

    @Override
    protected String buildRequestUrl() {
        return host;
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
