package com.trip.booking.spa.core.api.huitravel.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.huitravel.bean.Head;
import com.trip.booking.spa.core.api.huitravel.bean.HuiTravelBaseRequest;
import com.trip.booking.spa.core.api.huitravel.bean.hotel.detail.HotelDetailRequest;
import com.trip.booking.spa.core.api.huitravel.bean.hotel.detail.HotelDetailResponse;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityRequest;
import com.trip.booking.spa.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.trip.booking.spa.core.util.Md5Utils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class GetPriceAccess extends BaseHttpAccess<AvailabilityRequest, AvailabilityResponse> {
    private String host;

    private String appKey;

    private String secretKey;

    public GetPriceAccess(String host, String appKey, String secretKey,int retries) {
        super(SupplierSourceEnum.HUITRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, retries);
        this.host = host;
        this.appKey = appKey;
        this.secretKey = secretKey;
    }

    @Override
    protected ResponseResult<AvailabilityResponse> request(String url, AvailabilityRequest request, IParser<AvailabilityResponse> parser) throws Exception {
        long timestamp = System.currentTimeMillis();
        String sign = Md5Utils.md5Hex(Md5Utils.md5Hex(appKey + secretKey) + timestamp);
        HuiTravelBaseRequest baseRequest = HuiTravelBaseRequest.builder()
                .head(Head.builder().appKey(appKey).timestamp(timestamp + "").sign(sign).build())
                .data(request)
                .build();
        ResponseResult<AvailabilityResponse> result = HttpUtils.access(url, new HashMap<>(), JsonUtils.writeObject2Json(baseRequest), parser);
        if (!result.getData().getCode().equals("0")) {
            log.info("HuiTravel getPrice error: " + result.getData().getMsg());
        }
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
