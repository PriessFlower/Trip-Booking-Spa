package com.trip.booking.spa.core.api.didatravel.access;

import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.didatravel.bean.UrlDTO;
import com.trip.booking.spa.core.util.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 请求道旅获取静态数据.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/10
 * @since : 1.0
 **/
@Slf4j
public class StaticInfoAccess extends BaseHttpAccess<Map<String, Object>, UrlDTO> {

    private String host;


    public StaticInfoAccess(String host) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_API_HOTEL_LIST, 0);
        this.host = host;
    }

    @Override
    protected ResponseResult<UrlDTO> request(String url, Map<String, Object> request, IParser<UrlDTO> parser) throws Exception {
        long start = System.currentTimeMillis();
        ResponseResult<UrlDTO> result = HttpUtils.access(url, null, JsonUtils.writeObject2Json(request), parser);
        log.info("道旅查询静态数据接口耗时：{}", System.currentTimeMillis() - start);
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
    protected UrlDTO parseResponse(String data) {
        try {
            return new UrlDTO().setUrl(JSONObject.parseObject(data).get("Url").toString());
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
