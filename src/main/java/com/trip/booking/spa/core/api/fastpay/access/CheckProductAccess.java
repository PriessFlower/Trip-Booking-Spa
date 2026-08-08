package com.trip.booking.spa.core.api.fastpay.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.fastpay.bean.request.CheckPriceRequest;
import com.trip.booking.spa.core.api.fastpay.bean.response.CheckPriceResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class CheckProductAccess extends BaseHttpAccess<CheckPriceRequest, CheckPriceResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/booking/livecheck";

    private static int QPS = 100;

    public CheckProductAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.FASTPAYHOTELS, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
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
        log.info("fastpay checkproduct request:{} response: {}", JsonUtils.writeObject2Json(request), result);
        CheckPriceResponse searchResponse = JsonUtils.readValue(result, CheckPriceResponse.class);
        return new ResponseResult<>(searchResponse);
    }

    @Override
    protected void beforeAccess(CheckPriceRequest request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
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
