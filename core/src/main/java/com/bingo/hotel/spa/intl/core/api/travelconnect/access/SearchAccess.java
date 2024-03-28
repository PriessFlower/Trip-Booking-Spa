package com.bingo.hotel.spa.intl.core.api.travelconnect.access;

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

import java.util.Map;

public class SearchAccess extends BaseHttpAccess<SearchRequest, SearchResponse> {
    private String host;

    private String companyId;

    private String signKey;

    public SearchAccess(String host, String companyId, String signKey) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.companyId = companyId;
        this.signKey = signKey;
    }

    @Override
    protected ResponseResult<SearchResponse> request(String url, SearchRequest request, IParser<SearchResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("X-CompanyId", companyId);
        headers.put("X-SignKey", signKey);
        ResponseResult<SearchResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
        SearchResponse response = result.getData();
        response.setCheckInDate(request.getCheckindate());
        response.setCheckOutDate(request.getCheckoutdate());
        result.setData(response);
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
    protected SearchResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, SearchResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
