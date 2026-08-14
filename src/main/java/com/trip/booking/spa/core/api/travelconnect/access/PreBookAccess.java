package com.trip.booking.spa.core.api.travelconnect.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.travelconnect.bean.prebook.request.PrebookRequest;
import com.trip.booking.spa.core.api.travelconnect.bean.prebook.response.PrebookResponse;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class PreBookAccess extends BaseHttpAccess<PrebookRequest, PrebookResponse> {
    private String host;

    private String companyId;

    private String signKey;

    public PreBookAccess(String host, String companyId, String signKey) {
        super(SupplierSourceEnum.TRAVELCONNECT, SupplierDataTypeEnum.CHECK_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
        this.companyId = companyId;
        this.signKey = signKey;
    }

    @Override
    protected ResponseResult<PrebookResponse> request(String url, PrebookRequest request, IParser<PrebookResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("X-CompanyId", companyId);
        headers.put("X-SignKey", signKey);
        long start = System.currentTimeMillis();
        ResponseResult<PrebookResponse> result = HttpUtils.access(url, headers, JsonUtils.writeObject2Json(request), parser);
        PrebookResponse response = result.getData();
        response.setCheckInDate(request.getCheckindate());
        response.setCheckOutDate(request.getCheckoutdate());
        result.setData(response);
        log.info("travelConnect de PreBook request:{} response: {},UseTime:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result), System.currentTimeMillis() - start);

        return result;
    }

    @Override
    protected void beforeAccess(PrebookRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected PrebookResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, PrebookResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
