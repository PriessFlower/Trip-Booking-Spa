package com.bingo.hotel.spa.intl.core.api.expedia.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.QueryBedTypeResponse;

import java.util.Map;

/**
 * 静态数据接入.
 *
 * @author : hanJH
 * @version : 1.0 2024/08/16
 * @since : 1.0
 **/
public class StaticInfoAccess extends BaseHttpAccess<Map<String, Object>, QueryBedTypeResponse> {


    public StaticInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey) {
        super(supplier, dataType, monitorKey);
    }

    public StaticInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey, int retries) {
        super(supplier, dataType, monitorKey, retries);
    }

    @Override
    protected ResponseResult<QueryBedTypeResponse> request(String url, Map<String, Object> request, IParser<QueryBedTypeResponse> parser) throws Exception {
        return null;
    }

    @Override
    protected void beforeAccess(Map<String, Object> request) {

    }

    @Override
    protected String buildRequestUrl() {
        return null;
    }

    @Override
    protected QueryBedTypeResponse parseResponse(String data) {
        return null;
    }
}
