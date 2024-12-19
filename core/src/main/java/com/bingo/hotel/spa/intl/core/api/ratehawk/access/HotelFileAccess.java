package com.bingo.hotel.spa.intl.core.api.ratehawk.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.BaseResult;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Map;

@Slf4j
public class HotelFileAccess extends BaseHttpAccess<HotelInfoRequest, HotelFileResponse> {

    private String host;

    private String authorization;

    private DistributedRateLimiter redisRateLimiter;

    //首次全量地址
    private final static String PATH = "/hotel/info/dump/";
    //后续增量地址
//    private final static String PATH = "/hotel/info/incremental_dump/";

    private static int QPS = 30;

    public static String generateBasicAuth(String keyId, String apiKey) {
        String credentials = keyId + ":" + apiKey;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    public HotelFileAccess(String host, String authorization, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.RATEHAWK, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.authorization = authorization;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<HotelFileResponse> request(String url, HotelInfoRequest request, IParser<HotelFileResponse> parser) throws Exception {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json");
        Map<String, Object> body = Maps.newHashMap();
        body.put("inventory", request.getInventory());
        body.put("language", request.getLanguage());
        String hotelFileStr = HttpUtils.doPostObject(url, body, headers);
        BaseResult<HotelFileResponse> hotelFileResponse = JsonUtils.decodeJson(hotelFileStr, new TypeReference<>() {
        });
        if (null == hotelFileResponse || null == hotelFileResponse.getData() || "error".equals(hotelFileResponse.getError())) {
            log.info("ratehawk酒店静态查询异常 request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(hotelFileStr));
            return null;
        }
        return new ResponseResult<>(hotelFileResponse.getData());
    }

    @Override
    protected void beforeAccess(HotelInfoRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected HotelFileResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelFileResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    public static void main(String[] args) {
        try {
            Map<String, Object> headers = Maps.newHashMap();
            headers.put("Authorization", generateBasicAuth("10501", "1c79daa2-1262-4cf2-8547-3595628de48c"));
            headers.put("Content-Type", "application/json");
            Map<String, Object> body = Maps.newHashMap();
            body.put("inventory", "preferable");
            body.put("language", "en");
            String hotelFileStr = HttpUtils.doPostObject("https://api.worldota.net/api/b2b/v3/hotel/info/dump/", body, headers);
            BaseResult<HotelFileResponse> hotelFileResponse = JsonUtils.decodeJson(hotelFileStr, new TypeReference<>() {
            });
            if (null == hotelFileResponse || null == hotelFileResponse.getData() || "error".equals(hotelFileResponse.getError())) {
                return;
            }
            System.out.println(JsonUtils.writeObject2Json(hotelFileResponse));
        } catch (Exception e) {
            log.error("异常信息", e);
        }

    }

}
