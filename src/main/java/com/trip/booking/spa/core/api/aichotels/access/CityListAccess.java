package com.trip.booking.spa.core.api.aichotels.access;

import com.trip.booking.spa.core.api.aichotels.bean.hotel.city.CityListResponse;
import com.trip.booking.spa.core.api.aichotels.bean.hotel.single.SingleHotelResponse;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.travelconnect.bean.search.request.SearchRequest;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;

public class CityListAccess extends BaseHttpAccess<SearchRequest, CityListResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public CityListAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_CITY, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<CityListResponse> request(String url, SearchRequest request, IParser<CityListResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        ResponseResult<CityListResponse> result = HttpUtils.accessGet(url, headers, new HashMap<>(), parser);
        return result;
    }

    @Override
    protected void beforeAccess(SearchRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected CityListResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, CityListResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
