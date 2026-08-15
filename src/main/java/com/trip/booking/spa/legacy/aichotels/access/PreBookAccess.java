package com.trip.booking.spa.legacy.aichotels.access;

import com.trip.booking.spa.legacy.aichotels.bean.price.availability.AvailabilityRequest;
import com.trip.booking.spa.legacy.aichotels.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookRequest;
import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PreBookAccess extends BaseHttpAccess<PreBookRequest, PreBookResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public PreBookAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.CHECK_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<PreBookResponse> request(String url, PreBookRequest request, IParser<PreBookResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        long start = System.currentTimeMillis();
        ResponseResult<PreBookResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
        log.info("AicHotels de PreBook request:{} response: {},UseTime:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result), System.currentTimeMillis() - start);
        return result;
    }

    @Override
    protected void beforeAccess(PreBookRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected PreBookResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, PreBookResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

}