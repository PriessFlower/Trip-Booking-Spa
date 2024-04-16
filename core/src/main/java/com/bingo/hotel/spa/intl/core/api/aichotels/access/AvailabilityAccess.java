package com.bingo.hotel.spa.intl.core.api.aichotels.access;

import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
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
public class AvailabilityAccess extends BaseHttpAccess<AvailabilityRequest, AvailabilityResponse> {
    private String host;

    private String apiClientKey;

    private String date;

    private String apiClientToken;

    public AvailabilityAccess(String host, String apiClientKey, String Date, String apiClientToken) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_INFO, 0);
        this.host = host;
        this.apiClientKey = apiClientKey;
        this.date = Date;
        this.apiClientToken = apiClientToken;
    }

    @Override
    protected ResponseResult<AvailabilityResponse> request(String url, AvailabilityRequest request, IParser<AvailabilityResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("APIClientKey", apiClientKey);
        headers.put("Date", date);
        headers.put("APIClientToken", apiClientToken);
        headers.put("Content-Type", "application/json");
        ResponseResult<AvailabilityResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
        log.info("AicHotels de Availability response: " + JsonUtils.writeObject2Json(result));
        AvailabilityResponse response = result.getData();
        response.setHotelCode(request.getHotel_id() + "");
        result.setData(response);
        return result;
    }

    @Override
    protected void beforeAccess(AvailabilityRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected AvailabilityResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, AvailabilityResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

}
