package com.bingo.hotel.spa.intl.core.api.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelRequest;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DidaTravelAccess extends BaseHttpAccess<DidaTravelRequest, DidaTravelResponse> {

    private String host;

    public DidaTravelAccess(String host) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
    }

    @Override
    protected ResponseResult<DidaTravelResponse> request(String url, DidaTravelRequest request, IParser<DidaTravelResponse> parser) throws Exception {
        long start = System.currentTimeMillis();
        ResponseResult<DidaTravelResponse> result = null;
        try {
            result = HttpUtils.access(url, null, JSON.toJSONString(request), parser);
            if(result == null || result.getData() == null || result.getData().getSuccess()==null || !result.isSucc()){
                log.error("道旅报价接口异常，用时：{}，请求参数：{}", System.currentTimeMillis() - start, JSON.toJSONString(request));
                return result;
            }
        } catch (Exception e){
            log.error("道旅报价接口异常，用时：{}，请求参数：{}", System.currentTimeMillis() - start, JSON.toJSONString(request),e);
            return result;
        }
        log.info("DidaTravel接口耗时：{}", System.currentTimeMillis() - start);
        return result;
    }

    @Override
    protected void beforeAccess(DidaTravelRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected DidaTravelResponse parseResponse(String data) {
        DidaTravelResponse response = JSONObject.parseObject(data, DidaTravelResponse.class);
        return response;
    }
}
