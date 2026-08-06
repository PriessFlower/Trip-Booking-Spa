package com.trip.booking.spa.core.api.meituan.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.meituan.bean.request.CheckReqBody;
import com.trip.booking.spa.core.api.meituan.bean.request.MeituanRequest;
import com.trip.booking.spa.core.api.meituan.bean.response.CheckPriceResponse;
import com.trip.booking.spa.core.exception.RedisLimitException;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

/**
 * 验价接口
 *
 * @author baoys
 */
@Slf4j
public class CheckPriceAccess extends BaseHttpAccess<CheckReqBody, CheckPriceResponse> {

    private String host;

    private Integer partnerId;

    private String publicKey;

    private String secretKey;

    private String test;

    private String path;
    private String version;
    private static int QPS = 10;
    private final DistributedRateLimiter redisRateLimiter;

    public CheckPriceAccess(String host, Integer partnerId, String publicKey, String secretKey,
                            String test, String path, String version, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.MEITUAN, SupplierDataTypeEnum.CHECK_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
        this.partnerId = partnerId;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.test = test;
        this.path = path;
        this.version = version;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    public String buildRequestUrl() {
        return host;
    }


    @Override
    protected ResponseResult<CheckPriceResponse> request(String url, CheckReqBody body,
                                                         IParser<CheckPriceResponse> parser) throws Exception {

        MeituanRequest request = MeituanRequest.buildRequest(path, version, System.currentTimeMillis() / 1000,
                super.random.nextInt(Integer.MAX_VALUE), partnerId, publicKey, secretKey, test, body);

        if (request == null) {
            log.error(this.getClass().getName() + " request is error");
            return null;
        }
        ResponseResult<CheckPriceResponse> result = HttpUtils.access(url, Maps.newHashMap(), JsonUtils.writeObject2Json(request), parser);
        log.info("meituan checkprice request:{} response: {}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
        return result;
    }

    @Override
    protected void beforeAccess(CheckReqBody request) {
        if (!redisRateLimiter.tryAcquire(buildGlobalLimitKey(), QPS, RateIntervalUnit.SECONDS, WINDOW_IN_SECONDS, 3)) {
            throw new RedisLimitException("Request exceeds limit key = " + buildGlobalLimitKey()
                    + "request = " + JsonUtils.writeObject2Json(request));
        }
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
