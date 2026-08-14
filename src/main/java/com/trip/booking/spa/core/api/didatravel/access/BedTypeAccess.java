package com.trip.booking.spa.core.api.didatravel.access;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.didatravel.bean.QueryBedTypeResponse;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 床型枚举查询.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/15
 * @since : 1.0
 **/
@Slf4j
public class BedTypeAccess extends BaseHttpAccess<Map<String, Object>, QueryBedTypeResponse> {

    private String host;

    public BedTypeAccess(String host) {
        super(SupplierSourceEnum.DIDATRAVEL, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_BED_TYPE, 0);
        this.host = host;
    }

    @Override
    protected ResponseResult<QueryBedTypeResponse> request(String url, Map<String, Object> request, IParser<QueryBedTypeResponse> parser) throws Exception {
        long start = System.currentTimeMillis();
        ResponseResult<QueryBedTypeResponse> result = HttpUtils.access(url, null, JsonUtils.writeObject2Json(request), parser);
        log.info("道旅查询床型接口耗时：{}", System.currentTimeMillis() - start);
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
    protected QueryBedTypeResponse parseResponse(String data) {
        try {
            return JSON.parseObject(data, QueryBedTypeResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
