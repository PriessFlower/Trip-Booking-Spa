package com.bingo.hotel.spa.intl.core.api.expedia.access;

import com.bingo.hotel.base.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelIdsResponse;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HotelRemoveAccess extends BaseHttpAccess<String, HotelIdsResponse> {
    private String host;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/v3/properties/inactive";

    private static int QPS = 500;

    public HotelRemoveAccess(String host, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INCR, 0);
        this.host = host;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected ResponseResult<HotelIdsResponse> request(String url, String request, IParser<HotelIdsResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("since", request);
        ResponseResult result = HttpUtils.accessGet(url, headers, body, parser);
        return result;
    }

    @Override
    protected void beforeAccess(String request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected HotelIdsResponse parseResponse(String data) {
        try {
            HotelIdsResponse hotelIdsResponse = new HotelIdsResponse();
            hotelIdsResponse.setHotelIds(JsonUtils.decodeJson(data, new TypeReference<List<HotelIdsResponse.Property_id>>() {
            }));
            return hotelIdsResponse;
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
