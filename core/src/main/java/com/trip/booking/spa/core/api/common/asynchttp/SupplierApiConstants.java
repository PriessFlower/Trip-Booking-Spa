package com.trip.booking.spa.core.api.common.asynchttp;

public class SupplierApiConstants {
    
    //ERROR_TAG包括TIMEOUT_TAG、HTTP_ERROR_TAG、BIZ_ERROR_TAG、PARSE_ERROR_TAG
    //SUCC_TAG包含EMPTY_RESULT_TAG
	public static final String ACCESS_TAG = "access";//访问次数
	public static final String ACCESS_RETRE_TAG = "access_retry";//retry访问次数
    public static final String SUCC_TAG = "succ";//http和biz状态是OK
    public static final String ERROR_TAG = "error";//异常（包括所有异常）
    
    public static final String EMPTY_RESULT_TAG = "empty_result";//http和biz状态是OK，且数据为空
    
    public static final String TIMEOUT_TAG = "timeout";//超时
    public static final String HTTP_ERROR_TAG = "http_error";//http状态非200
    public static final String HTTP_STATUS_TAG_PREFIX = "http_status_";//http具体状态码
    public static final String BIZ_ERROR_TAG = "biz_error";//业务状态异常

    public static final String PROMISE_ERROR_TAG = "promise_future_error";//promise future异常
    public static final String PARSE_ERROR_TAG = "parse_error";//解析异常
    
    public static final String REJECT_TAG = "thread_pool_submit_task_reject";//线程池执行拒绝异常
}
