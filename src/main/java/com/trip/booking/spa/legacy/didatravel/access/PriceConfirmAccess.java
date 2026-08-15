package com.trip.booking.spa.legacy.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.platform.http.BaseHttpAccess;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.legacy.didatravel.bean.price.priceConfirm.PriceConfirmRequest;
import com.trip.booking.spa.legacy.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.trip.booking.spa.platform.http.HttpUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceConfirmAccess extends BaseHttpAccess<PriceConfirmRequest, PriceConfirmResponse> {

    private String host;
    public PriceConfirmAccess(String host) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
        this.host = host;
    }

    @Override
    protected ResponseResult<PriceConfirmResponse> request(String url, PriceConfirmRequest request, IParser<PriceConfirmResponse> parser) throws Exception {
        ResponseResult<PriceConfirmResponse> result = HttpUtils.access(url, null, JSON.toJSONString(request), parser);
        log.info("daolv priceConfirm request:{} response: {}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
        return result;
    }

    @Override
    protected void beforeAccess(PriceConfirmRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected PriceConfirmResponse parseResponse(String data) {
        JSONObject jsonObject = JSONObject.parseObject(data);
        if(jsonObject.containsKey("Error")){
            JSONObject error = jsonObject.getJSONObject("Error");
            String message = error.getString("Message");
            throw new RuntimeException(message);
        }
        PriceConfirmResponse response = JSONObject.parseObject(data, PriceConfirmResponse.class);
        return response;
    }
}
