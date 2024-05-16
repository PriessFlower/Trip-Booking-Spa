package com.bingo.hotel.spa.intl.core.api.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 查询道旅报价相关信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/
@Slf4j
public class SearchAccess extends BaseHttpAccess<Map<String, Object>, CheckPriceResponse> {


    private String host;


    public SearchAccess(String host) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.PRODUCT_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
        this.host = host;
    }

    @Override
    protected ResponseResult<CheckPriceResponse> request(String url, Map<String, Object> request, IParser<CheckPriceResponse> parser) throws Exception {
        long start = System.currentTimeMillis();
//        log.info("道旅查询报价接口 request：{}", JsonUtils.writeObject2Json(request));
        ResponseResult<CheckPriceResponse> result = HttpUtils.access(url, null, JsonUtils.writeObject2Json(request), parser);
//        log.info("道旅查询报价接口 response：{}", JsonUtils.writeObject2Json(result));
        log.info("道旅查询报价接口耗时：{}", System.currentTimeMillis() - start);
        return result;
    }

    @Override
    protected void beforeAccess(Map<String, Object> request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host;
    }

    @Override
    protected CheckPriceResponse parseResponse(String data) {
        try {
            return JSON.parseObject(data, CheckPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
