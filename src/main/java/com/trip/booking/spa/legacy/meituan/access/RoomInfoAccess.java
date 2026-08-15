
package com.trip.booking.spa.legacy.meituan.access;


import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.meituan.bean.request.HotelInfoReqBody;
import com.trip.booking.spa.legacy.meituan.bean.request.MeituanRequest;
import com.trip.booking.spa.legacy.meituan.bean.response.RoomInfoResponse;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;


/**
 * room list接口
 *
 * @author Hjh
 */
@Slf4j
public class RoomInfoAccess extends BaseHttpAccess<HotelInfoReqBody, RoomInfoResponse> {

    private String host;

    private Integer partnerId;

    private String publicKey;

    private String secretKey;

    private String test;

    private String path;
    private String version;
    private static int QPS = 20;
    private final DistributedRateLimiter redisRateLimiter;


    public RoomInfoAccess(String host, Integer partnerId, String publicKey, String secretKey,
                          String test, String path, String version, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.MEITUAN, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_ROOM_INFO, 0);
        this.host = host;
        this.partnerId = partnerId;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.test = test;
        this.path = path;
        this.version = version;
        this.redisRateLimiter = redisRateLimiter;
        ;
    }

    @Override
    public String buildRequestUrl() {
        return host;
    }


    @Override
    protected ResponseResult<RoomInfoResponse> request(String url, HotelInfoReqBody body,
                                                       IParser<RoomInfoResponse> parser) throws Exception {

        MeituanRequest request = MeituanRequest.buildRequest(path, version, System.currentTimeMillis() / 1000,
                random.nextInt(Integer.MAX_VALUE), partnerId, publicKey, secretKey, test, body);

        if (request == null) {
            log.error(this.getClass().getName() + " request is error");
            return null;
        }

        return HttpUtils.access(url, Maps.newHashMap(), JsonUtils.writeObject2Json(request), parser);
    }

    @Override
    protected void beforeAccess(HotelInfoReqBody request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }


    @Override
    protected RoomInfoResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, RoomInfoResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

}
