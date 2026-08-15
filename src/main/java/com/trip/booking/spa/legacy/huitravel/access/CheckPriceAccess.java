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
import com.trip.booking.spa.legacy.huitravel.bean.price.check.CheckRequest;
import com.trip.booking.spa.legacy.huitravel.bean.price.check.CheckResponse;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.platform.util.Md5Utils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class CheckPriceAccess extends BaseHttpAccess<CheckRequest, CheckResponse> {
    private String host;

    private String appKey;

    private String secretKey;

    public CheckPriceAccess(String host, String appKey, String secretKey) {
        super(SupplierSourceEnum.HUITRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
        this.appKey = appKey;
        this.secretKey = secretKey;
    }

    @Override
    protected ResponseResult<CheckResponse> request(String url, CheckRequest request, IParser<CheckResponse> parser) throws Exception {
        long timestamp = System.currentTimeMillis();
        String sign = Md5Utils.md5Hex(Md5Utils.md5Hex(appKey + secretKey) + timestamp);
        HuiTravelBaseRequest baseRequest = HuiTravelBaseRequest.builder()
                .head(Head.builder().appKey(appKey).timestamp(timestamp + "").sign(sign).build())
                .data(request)
                .build();
        ResponseResult<CheckResponse> result = HttpUtils.access(url, new HashMap<>(), JsonUtils.writeObject2Json(baseRequest), parser);
        log.info("huizhi checkprice request:{} response: {}", JsonUtils.writeObject2Json(baseRequest), JsonUtils.writeObject2Json(result));
        return result;
    }

    @Override
    protected void beforeAccess(CheckRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected CheckResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, CheckResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
