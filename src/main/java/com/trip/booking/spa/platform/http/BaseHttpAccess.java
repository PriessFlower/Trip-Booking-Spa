package com.trip.booking.spa.platform.http;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.exception.RedisLimitException;
import com.trip.booking.spa.platform.observability.CallStatus;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.base.Joiner;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
        // 统一限流：所有供应商 HTTP 调用的唯一闸门。QPS 配在 Nacos，key = 供应商_接口。
        String limitKey = buildGlobalLimitKey();
        if (!RateLimitHolder.get().tryAcquire(limitKey)) {
            Monitor.recordOne(MetricNames.SUPPLIER_IO_ACCESS, ioTags(CallStatus.THROTTLED));
            throw new RedisLimitException("Request exceeds rate limit, key = " + limitKey);
        }
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
        // query() 在全部重试均抛异常时返回 null（读超时、连接重置、SSL 失败等）。此处兜底为失败态，
        // 而非让调用方在 result.getData() 上空指针——网络超时是常态，不应表现为 NPE。
        // 返回值 httpStatus != 200 且 data == null，isSucc() 为 false，与既有守卫写法一致。
        // 终态在此处一次判定、一次记录（O-3.1/O-3.3）：四个分支互斥且穷尽，
        // 故 sum by (status) 等于调用总数，可直接做各类比率的分母。
        //
        // 此前有两处失真：① 记了 empty 之后又无条件补记一次 ok，于是 ok 的实际含义是
        // 「全部调用」而非「非空调用」，空结果占比被系统性低估；② 只要 result 非 null
        // 就记 ok，HTTP 非 200 与业务错误码也被算成成功。
        long cost = System.currentTimeMillis() - start;
        if (null == result) {
            // 重试全部抛异常才会到这儿。超时与连接/解析失败目前混在一处——底层把它们
            // 都抛成普通 Exception，要分出 TIMEOUT 得先在 request() 里辨别异常类型（欠账）
            Monitor.recordOne(MetricNames.SUPPLIER_IO_ACCESS, ioTags(CallStatus.ERROR), cost);
            logger.error("access fail, 重试已耗尽, supplier:[{}], interface:[{}], url:[{}]", supplier, monitorKey, url);
            return new ResponseResult<>(HttpStatus.SC_GATEWAY_TIMEOUT, null);
        }
        if (!result.isSucc()) {
            Monitor.recordOne(MetricNames.SUPPLIER_IO_ACCESS, ioTags(CallStatus.REJECTED), cost);
            return result;
        }
        if (null != result.getData() && result.getData().isEmptyResult()) {
            Monitor.recordOne(MetricNames.SUPPLIER_IO_ACCESS, ioTags(CallStatus.NO_INVENTORY), cost);
            return result;
        }
        Monitor.recordOne(MetricNames.SUPPLIER_IO_ACCESS, ioTags(CallStatus.QUOTED), cost);
        return result;
    }

    private Map<String, Object> ioTags(CallStatus status) {
        return MetricTags.of(supplier, monitorKey, status);
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
                    Monitor.recordOne(MetricNames.SUPPLIER_IO_RETRY, MetricTags.of(supplier, monitorKey));
                }
                result = this.request(url, request, parser);
                if (result.isSucc()) {
                    break;
                }
            } catch (Exception e) {
                // 这里<b>不</b>记 supplier_io_access：那个指标的语义是「一次调用的终态」，
                // 一次调用只许记一次（O-3.1）。此前每抛一次异常记一条、末尾 result==null 再记一条，
                // 一次逻辑调用最多产生 N+1 条，成功率与耗时都被重试次数污染。
                // 失败尝试的次数由 supplier_io_retry 承载，终态由 access() 统一判定。
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
