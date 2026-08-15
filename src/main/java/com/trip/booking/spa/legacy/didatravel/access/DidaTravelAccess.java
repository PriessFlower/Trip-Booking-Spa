package com.trip.booking.spa.legacy.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.legacy.didatravel.bean.price.DidaTravelRequest;
import com.trip.booking.spa.legacy.didatravel.bean.price.DidaTravelResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

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
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
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
