package com.bingo.hotel.spa.intl.core.api.aichotels.access;

import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.list.HotelListResponse;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.request.SearchRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;

public class HotelListAccess extends BaseHttpAccess<SearchRequest, HotelListResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public HotelListAccess(String host, String apiClientKey, String date, String apiClientToken) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<HotelListResponse> request(String url, SearchRequest request, IParser<HotelListResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
//        headers.put("Content-Type", "application/json");
        ResponseResult<HotelListResponse> result = HttpUtils.accessGet(url, headers, new HashMap<>(), parser);
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
    protected HotelListResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelListResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
