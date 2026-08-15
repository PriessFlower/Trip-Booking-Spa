package com.trip.booking.spa.legacy.aichotels.access;

import com.trip.booking.spa.legacy.aichotels.bean.hotel.room.RoomInfoResponse;
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

public class RoomInfoAccess extends BaseHttpAccess<SearchRequest, RoomInfoResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public RoomInfoAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.AICHOTELS, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_ROOM_INFO, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<RoomInfoResponse> request(String url, SearchRequest request, IParser<RoomInfoResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        ResponseResult<RoomInfoResponse> result = HttpUtils.accessGet(url, headers, new HashMap<>(), parser);
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
    protected RoomInfoResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, RoomInfoResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
