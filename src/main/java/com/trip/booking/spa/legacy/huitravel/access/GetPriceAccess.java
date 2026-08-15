package com.trip.booking.spa.legacy.huitravel.access;

import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.legacy.huitravel.bean.Head;
import com.trip.booking.spa.legacy.huitravel.bean.HuiTravelBaseRequest;
import com.trip.booking.spa.legacy.huitravel.bean.hotel.detail.HotelDetailRequest;
import com.trip.booking.spa.legacy.huitravel.bean.hotel.detail.HotelDetailResponse;
import com.trip.booking.spa.legacy.huitravel.bean.price.availability.AvailabilityRequest;
import com.trip.booking.spa.legacy.huitravel.bean.price.availability.AvailabilityResponse;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.platform.util.Md5Utils;
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
