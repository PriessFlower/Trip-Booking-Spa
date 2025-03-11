package com.bingo.hotel.spa.intl.core.api.common.access;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.SupplierApiConstants;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.monitor.Monitor;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;


/**
 * @param <T>
 * @author zhe.hao
 */
public abstract class BaseHttpAccess<U, T extends BaseResponse> {

    private static String LIMIT_PREFIX = "GLOBAL_LIMIT";

    public static final Joiner JOINER = Joiner.on("_").skipNulls();

    protected static final Random random = new Random();

    protected static final long WINDOW_IN_SECONDS = 1;

    private static Logger logger = LoggerFactory.getLogger(BaseHttpAccess.class);

    private int retries = 0;// 重试次数,0不进行重试，如果为1，则最多请求两次
    private SupplierSourceEnum supplier;
    private SupplierDataTypeEnum dataType;
    private MonitorNameEnum monitorKey;

    public BaseHttpAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey) {
        this.supplier = supplier;
        this.dataType = dataType;
        this.monitorKey = monitorKey;
    }

    public BaseHttpAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                          int retries) {
        this(supplier, dataType, monitorKey);
        this.retries = retries;
    }

    public ResponseResult<T> access(U request) {
        long start = System.currentTimeMillis();
        beforeAccess(request);
        String url = buildRequestUrl();
        ResponseResult<T> result = this.query(url, request, new IParser<T>() {
            @Override
            public T parse(String data) throws ParseException {
                return parseResponse(data);

            }

            @Override
            public T parseError(String data) throws ParseException {
                if (isParseError()) {
                    return parseResponse(data);
                }
                return null;
            }
        });
        if (null != result.getData() && result.getData().isEmptyResult()) {
            Monitor.recordOne(buildMonitorKey(SupplierApiConstants.ACCESS_TAG) + "_empty",
                    System.currentTimeMillis() - start);
        }
        Monitor.recordOne(buildMonitorKey(SupplierApiConstants.ACCESS_TAG), System.currentTimeMillis() - start);
        return result;
    }

    private String buildMonitorKey(String tag) {
        return JOINER.join(supplier.name(), monitorKey.name(), tag);
    }

    private String buildMonitorKey(String tag, String status) {
        return JOINER.join(supplier.name(), monitorKey.name(), tag, status);
    }

    public boolean isParseError() {
        return false;
    }

    private ResponseResult<T> query(String url, U request, IParser<T> parser) {
        this.logQuery(url, request);
        int count = 0;
        ResponseResult<T> result = null;
        do {
            try {
                count++;
                if (count > 1) {
                    Monitor.recordOne(buildMonitorKey(SupplierApiConstants.ACCESS_RETRE_TAG));
                }
                result = this.request(url, request, parser);
                if (result.isSucc()) {
                    break;
                }
            } catch (Exception e) {
                Monitor.recordOne(buildMonitorKey(SupplierApiConstants.ACCESS_TAG, SupplierApiConstants.ERROR_TAG));
                logger.error("query fail, supplier:[{}], interface:[{}], url:[{}] , request:[{}]",
                        supplier, monitorKey, url, JsonUtils.writeObject2Json(request), e);
            }
        } while (retries >= count);
        return result;
    }

    private void logQuery(String url, Object param) {
        if (logger.isDebugEnabled()) {
            logger.debug("url [{}], param:{}", url, param);
        }
    }

    protected String buildGlobalLimitKey() {
        return LIMIT_PREFIX + ":" + supplier.name() + ":" + monitorKey.name();
    }

    protected abstract ResponseResult<T> request(final String url, U request, IParser<T> parser) throws Exception;

    /**
     * 请求前的处理，如限流
     */
    protected abstract void beforeAccess(U request);

    protected abstract String buildRequestUrl();

    protected abstract T parseResponse(String data);

    protected Logger getOriginResponseLogger() {
        return null;
    }
}
