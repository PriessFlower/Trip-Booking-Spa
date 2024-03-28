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

import java.util.HashMap;
import java.util.Map;

public class PreBookAccess extends BaseHttpAccess<PreBookRequest, PreBookResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public PreBookAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
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
        ResponseResult<PreBookResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
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