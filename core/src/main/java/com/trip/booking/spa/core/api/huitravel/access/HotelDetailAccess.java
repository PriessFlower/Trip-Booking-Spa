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
import com.trip.booking.spa.core.api.huitravel.bean.hotel.detail.HotelDetailResult;
import com.trip.booking.spa.core.api.huitravel.bean.hotel.list.HotelListRequest;
import com.trip.booking.spa.core.api.huitravel.bean.hotel.list.HotelListResponse;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.trip.booking.spa.core.util.Md5Utils;

import java.util.HashMap;

public class HotelDetailAccess extends BaseHttpAccess<HotelDetailRequest, HotelDetailResponse> {
    private String host;

    private String appKey;

    private String secretKey;

    public HotelDetailAccess(String host, String appKey, String secretKey) {
        super(SupplierSourceEnum.HUITRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.appKey = appKey;
        this.secretKey = secretKey;
    }

    @Override
    protected ResponseResult<HotelDetailResponse> request(String url, HotelDetailRequest request, IParser<HotelDetailResponse> parser) throws Exception {
        long timestamp = System.currentTimeMillis();
        String sign = Md5Utils.md5Hex(Md5Utils.md5Hex(appKey + secretKey) + timestamp);
        HuiTravelBaseRequest baseRequest = HuiTravelBaseRequest.builder()
                .head(Head.builder().appKey(appKey).timestamp(timestamp + "").sign(sign).build())
                .data(request)
                .build();
        ResponseResult<HotelDetailResponse> result = HttpUtils.access(url, new HashMap<>(), JsonUtils.writeObject2Json(baseRequest), parser);
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
