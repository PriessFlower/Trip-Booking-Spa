package com.bingo.hotel.spa.intl.core.api.meituan.access;


import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.HotelIdsReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.MeituanRequest;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.HotelIdsResponse;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;

/**
 * hotel list接口
 *
 * @author zhe.hao
 */
@Slf4j
public class HotelListAccess extends BaseHttpAccess<HotelIdsReqBody, HotelIdsResponse> {

    private String host;

    private Integer partnerId;

    private String publicKey;

    private String secretKey;

    private String test;

    private String path;
    private String version;
    private static int QPS = 10;
    private final DistributedRateLimiter redisRateLimiter;


    public HotelListAccess(String host, Integer partnerId, String publicKey, String secretKey,
                           String test, String path, String version,  DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.MEITUAN, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
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
    protected ResponseResult<HotelIdsResponse> request(String url, HotelIdsReqBody body,
                                                       IParser<HotelIdsResponse> parser) throws Exception {

        MeituanRequest request = MeituanRequest.buildRequest(path, version, System.currentTimeMillis() / 1000,
                random.nextInt(Integer.MAX_VALUE), partnerId, publicKey, secretKey, test, body);

        if (request == null) {
            log.error(this.getClass().getName() + " request is error");
            return null;
        }
        log.info("HotelListAccess>>>request:{}", JsonUtils.writeObject2Json(request));
        return HttpUtils.access(url, Maps.newHashMap(), JsonUtils.writeObject2Json(request), parser);
    }

    @Override
    protected void beforeAccess(HotelIdsReqBody request) {
        if(!redisRateLimiter.tryAcquire(buildGlobalLimitKey(), QPS, RateIntervalUnit.SECONDS, WINDOW_IN_SECONDS,3)){
            throw new RedisLimitException("Request exceeds limit key = " + buildGlobalLimitKey()
                    + "request = " + JsonUtils.writeObject2Json(request));
        }
    }


    @Override
    protected HotelIdsResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelIdsResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }


}
