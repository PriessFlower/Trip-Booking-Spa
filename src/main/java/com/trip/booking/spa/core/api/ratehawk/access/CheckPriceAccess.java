package com.trip.booking.spa.core.api.ratehawk.access;


import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.ratehawk.bean.request.CheckPriceRequest;
import com.trip.booking.spa.core.api.ratehawk.bean.response.BaseResult;
import com.trip.booking.spa.core.api.ratehawk.bean.response.CheckPriceResponse;
import com.trip.booking.spa.core.api.ratehawk.bean.response.QueryProductResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 查询产品信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/09
 * @since : 1.0
 **/

@Slf4j
public class CheckPriceAccess extends BaseHttpAccess<CheckPriceRequest, CheckPriceResponse> {
    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    //查询率
//    private final static String PATH = "/search/lookuprate/";

    //预订
    private final static String PATH = "/hotel/prebook/";

    private static int QPS = 2;

    public CheckPriceAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.RATEHAWK, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
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
        body.put("hash", request.getBook_hash());
//        body.put("price_increase_percent", 0);
//        body.put("language", request.getLanguage());
        String result = HttpUtils.doPostObject(url, body, headers);
        log.info("ratehawk checkprice request:{} response: {}", JsonUtils.writeObject2Json(request), result);
        BaseResult<CheckPriceResponse> checkPriceResponse = JsonUtils.decodeJson(result, new TypeReference<>() {
        });
        if (null == checkPriceResponse || null == checkPriceResponse.getData() || "error".equals(checkPriceResponse.getError())) {
            log.info("ratehawk验价异常 request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
            return null;
        }
        return new ResponseResult<>(checkPriceResponse.getData());
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
