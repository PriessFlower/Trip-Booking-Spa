package com.trip.booking.spa.platform.http;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.http.asynchttp.SupplierApiConstants;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.exception.RedisLimitException;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.google.common.base.Joiner;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
            Monitor.recordOne(IO_METRIC, ioTags("limited"));
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
        if (null == result) {
            Monitor.recordOne(IO_METRIC, ioTags(SupplierApiConstants.ERROR_TAG));
            logger.error("access fail, 重试已耗尽, supplier:[{}], interface:[{}], url:[{}]", supplier, monitorKey, url);
            return new ResponseResult<>(HttpStatus.SC_GATEWAY_TIMEOUT, null);
        }
        if (null != result.getData() && result.getData().isEmptyResult()) {
            Monitor.recordOne(IO_METRIC, ioTags("empty"), System.currentTimeMillis() - start);
        }
        Monitor.recordOne(IO_METRIC, ioTags("ok"), System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 供应商 IO 的指标名。<b>固定一个名字，维度全部进 tag</b>（§3.9.2）。
     *
     * <p>原先是 {@code JOINER.join(supplier, 接口, tag, status)} 拼成名字，于是每个
     * (供应商 × 接口 × 状态) 组合都生成一个独立指标名——接十家供应商、每家五个接口、
     * 四种状态就是 200 个名字，正是 §3.9.2 要防的名字爆炸（反面即 cursor 的 324 种日志前缀）。
     * 改为固定名 + tag 后，接多少家供应商都还是这一个名字，按 tag 切片查询。
     */
    private static final String IO_METRIC = "supplier_io_access";

    /** 重试单独一个名字：它与"一次调用的结果"不是同一个度量，混在一个 counter 里会把成功率算错 */
    private static final String IO_RETRY_METRIC = "supplier_io_retry";

    private Map<String, Object> ioTags(String status) {
        Map<String, Object> tags = new HashMap<>(3);
        tags.put("supplier", supplier.name());
        tags.put("interface", monitorKey.name());
        tags.put("status", status);
        return tags;
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
                    Monitor.recordOne(IO_RETRY_METRIC, ioTags("retry"));
                }
                result = this.request(url, request, parser);
                if (result.isSucc()) {
                    break;
                }
            } catch (Exception e) {
                Monitor.recordOne(IO_METRIC, ioTags(SupplierApiConstants.ERROR_TAG));
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
