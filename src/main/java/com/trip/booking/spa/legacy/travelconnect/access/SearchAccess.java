package com.trip.booking.spa.legacy.travelconnect.access;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.travelconnect.bean.search.request.SearchRequest;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class SearchAccess extends BaseHttpAccess<SearchRequest, SearchResponse> {
    private String host;

    private String companyId;

    private String signKey;

    public SearchAccess(String host, String companyId, String signKey) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.PRODUCT_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICE, 0);
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
