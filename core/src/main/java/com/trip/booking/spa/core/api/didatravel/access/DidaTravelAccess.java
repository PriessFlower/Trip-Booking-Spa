package com.trip.booking.spa.core.api.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.didatravel.bean.price.DidaTravelRequest;
import com.trip.booking.spa.core.api.didatravel.bean.price.DidaTravelResponse;
import com.trip.booking.spa.core.exception.RedisLimitException;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

@Slf4j
public class DidaTravelAccess extends BaseHttpAccess<DidaTravelRequest, DidaTravelResponse> {

    private String host;

    private static int QPS = 50;

    private DistributedRateLimiter redisRateLimiter;

    public DidaTravelAccess(String host, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<DidaTravelResponse> request(String url, DidaTravelRequest request, IParser<DidaTravelResponse> parser) throws Exception {
        long start = System.currentTimeMillis();
        ResponseResult<DidaTravelResponse> result = null;
        try {
            result = HttpUtils.access(url, null, JSON.toJSONString(request), parser);
            if(result == null || result.getData() == null || result.getData().getSuccess()==null || !result.isSucc()){
                log.error("道旅报价接口异常，用时：{}，请求参数：{}，返回结果：{}", System.currentTimeMillis() - start,JSON.toJSONString(request), JSON.toJSONString(result) == null ? "null" : JSON.toJSONString(result));
                return result;
            }
        } catch (Exception e){
            log.error("道旅报价接口异常，用时：{}，请求参数：{},返回结果：{},异常信息：{}", System.currentTimeMillis() - start, JSON.toJSONString(request), JSON.toJSONString(result) == null ? "null" : JSON.toJSONString(result),e);
            return result;
        }
        log.info("DidaTravel接口耗时：{}", System.currentTimeMillis() - start);
        return result;
    }

    @Override
    protected void beforeAccess(DidaTravelRequest request) {
//        if (!redisRateLimiter.tryAcquire(buildGlobalLimitKey(), QPS, RateIntervalUnit.SECONDS, WINDOW_IN_SECONDS, 5)) {
//            log.info("DidaTravel接口请求超过限制，每秒请求超过{}次", QPS);
//            throw new RedisLimitException("Request exceeds limit key = " + buildGlobalLimitKey()
//                    + "request = " + JsonUtils.writeObject2Json(request));
//        }
    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected DidaTravelResponse parseResponse(String data) {
        DidaTravelResponse response = JSONObject.parseObject(data, DidaTravelResponse.class);
        return response;
    }
}
