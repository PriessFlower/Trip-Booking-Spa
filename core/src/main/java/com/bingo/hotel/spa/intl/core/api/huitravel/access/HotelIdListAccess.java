package com.bingo.hotel.spa.intl.core.api.huitravel.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.HuiTravelBaseRequest;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.Head;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list.HotelListRequest;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list.HotelListResponse;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.bingo.hotel.spa.intl.core.util.Md5Utils;

import java.util.HashMap;

public class HotelIdListAccess extends BaseHttpAccess<HotelListRequest, HotelListResponse> {
    private String host;

    private String appKey;

    private String secretKey;

    public HotelIdListAccess(String host, String appKey, String secretKey) {
        super(SupplierSourceEnum.HUITRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
        this.appKey = appKey;
        this.secretKey = secretKey;
    }

    @Override
    protected ResponseResult<HotelListResponse> request(String url, HotelListRequest request, IParser<HotelListResponse> parser) throws Exception {
        long timestamp = System.currentTimeMillis();
        String sign = Md5Utils.md5Hex(Md5Utils.md5Hex(appKey + secretKey) + timestamp);
        HuiTravelBaseRequest baseRequest = HuiTravelBaseRequest.builder()
                .head(Head.builder().appKey(appKey).timestamp(timestamp + "").sign(sign).build())
                .data(request)
                .build();
        ResponseResult<HotelListResponse> result = HttpUtils.access(url, new HashMap<>(), JsonUtils.writeObject2Json(baseRequest), parser);
        return result;
    }

    @Override
    protected void beforeAccess(HotelListRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected HotelListResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, HotelListResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
