package com.trip.booking.spa.core.api.travelconnect.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.travelconnect.bean.hotel.HotelDetailRequest;
import com.trip.booking.spa.core.api.travelconnect.bean.hotel.HotelDetailResponse;
import com.trip.booking.spa.core.api.travelconnect.bean.search.request.SearchRequest;
import com.trip.booking.spa.core.api.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;

import java.util.Map;

public class HotelDetailAccess extends BaseHttpAccess<HotelDetailRequest, HotelDetailResponse> {
    private String host;

    private String companyId;

    private String signKey;

    public HotelDetailAccess(String host, String companyId, String signKey) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.companyId = companyId;
        this.signKey = signKey;
    }

    @Override
    protected ResponseResult<HotelDetailResponse> request(String url, HotelDetailRequest request, IParser<HotelDetailResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("X-CompanyId", companyId);
        headers.put("X-SignKey", signKey);
        ResponseResult<HotelDetailResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);

        return result;
    }

    @Override
    protected void beforeAccess(HotelDetailRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected HotelDetailResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelDetailResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
