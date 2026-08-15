package com.trip.booking.spa.legacy.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.didatravel.bean.CheckPriceResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


/**
 * 查询道旅报价相关信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/
@Slf4j
public class SearchAccess extends BaseHttpAccess<Map<String, Object>, CheckPriceResponse> {


    private String host;

    private static int QPS = 3;

    private DistributedRateLimiter redisRateLimiter;

    public SearchAccess(String host, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.PRODUCT_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 1);
        this.host = host;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<CheckPriceResponse> request(String url, Map<String, Object> request, IParser<CheckPriceResponse> parser) throws Exception {
        long start = System.currentTimeMillis();
//        log.info("道旅查询报价接口 request：{}", JsonUtils.writeObject2Json(request));
        ResponseResult<CheckPriceResponse> result = HttpUtils.access(url, null, JsonUtils.writeObject2Json(request), parser);
//        log.info("道旅查询报价接口 response：{}", JsonUtils.writeObject2Json(result));
        log.info("道旅查询报价接口耗时：{}", System.currentTimeMillis() - start);
        return result;
    }

    @Override
    protected void beforeAccess(Map<String, Object> request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected CheckPriceResponse parseResponse(String data) {
        try {
            return JSON.parseObject(data, CheckPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
