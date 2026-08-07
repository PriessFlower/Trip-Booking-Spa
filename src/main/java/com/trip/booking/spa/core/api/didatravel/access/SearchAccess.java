package com.trip.booking.spa.core.api.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.didatravel.bean.CheckPriceResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
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
