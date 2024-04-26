package com.bingo.hotel.spa.intl.core.api.aichotels.access;

import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
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
        log.info("AicHotels de PreBook response: {},UseTime:{}", JsonUtils.writeObject2Json(result), System.currentTimeMillis() - start);
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