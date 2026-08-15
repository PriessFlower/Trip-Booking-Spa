package com.trip.booking.spa.legacy.aichotels.access;

import com.trip.booking.spa.legacy.aichotels.bean.hotel.list.HotelListResponse;
import com.trip.booking.spa.legacy.aichotels.bean.hotel.single.SingleHotelResponse;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.travelconnect.bean.search.request.SearchRequest;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;

public class SingleHotelAccess extends BaseHttpAccess<SearchRequest, SingleHotelResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public SingleHotelAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<SingleHotelResponse> request(String url, SearchRequest request, IParser<SingleHotelResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        ResponseResult<SingleHotelResponse> result = HttpUtils.accessGet(url, headers,new HashMap<>(), parser);
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
    protected SingleHotelResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, SingleHotelResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
