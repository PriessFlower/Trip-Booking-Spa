package com.trip.booking.spa.core.api.expedia.access;

import com.google.common.collect.Maps;
import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.expedia.bean.response.CancelRoomResponse;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 取消 Expedia 的单个房间。
 *
 * <p><b>取消是逐房进行的</b>：Expedia 不提供「取消整单」的接口，只在反查响应的
 * {@code rooms[].links.cancel.href} 上给出每间房各自的取消链接。故一笔多房订单需要多次
 * 调用，且可能出现部分成功——编排与部分成功的判读由适配层负责，本类只管发出一次取消。
 *
 * <p>链接自带 token，不可自行拼接，也不能跨订单复用。
 *
 * <p><b>关于重试</b>：与下单不同，取消是幂等的——重复取消同一间房，终态都是已取消，
 * 不会产生第二笔副作用。故此处允许一次重试以穿过瞬时抖动。但重试耗尽后仍失败时，
 * 不得断言「未取消」：请求可能已在 Expedia 侧生效而响应丢失，该情形须由适配层判 UNKNOWN。
 *
 * <p>成功时 Expedia 返回 <b>204 且无响应体</b>，故判定成功一律以状态码为准，
 * 不能以「有没有响应体」判断。
 */
@Slf4j
public class CancelRoomAccess extends BaseHttpAccess<String, CancelRoomResponse> {

    private final String cancelHref;
    private final String authorization;
    private final String customerIp;
    private final String customerSessionId;

    /**
     * @param cancelHref 反查响应中该房间的完整取消链接（含 token）
     */
    public CancelRoomAccess(String cancelHref, String authorization, String customerIp,
                            String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        // 重试次数固定 0。取消成功返回 204，而 ResponseResult.isSucc() 只认 200，
        // 于是成功会被判成失败并触发重试；第二次 DELETE 得到 400「Room is already cancelled」，
        // 最终把一次成功的取消上报成 UNKNOWN。实测（2026-08-14 沙箱）确实发出两次 DELETE。
        //
        // 取消虽然幂等、重复调用不产生第二笔副作用，但在框架把 204 判为失败的前提下，
        // 重试只会把正确结论改坏。待 ResponseResult 支持 2xx 判定后可再放开。
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.CANCEL_ORDER,
                MonitorNameEnum.SPA_SUPPLIER_API_CANCEL_ORDER, 0);
        this.cancelHref = cancelHref;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
    }

    @Override
    protected ResponseResult<CancelRoomResponse> request(String url, String request,
                                                         IParser<CancelRoomResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Accept", "application/json");
        ResponseResult result = HttpUtils.accessDelete(url, headers, parser);
        log.info("expedia cancelRoom href={} status={} body={}", url, result.getHttpStatus(), result.getOrigData());
        return result;
    }

    @Override
    protected void beforeAccess(String request) {
        // 限流由 BaseHttpAccess.access() 统一处理
    }

    @Override
    protected String buildRequestUrl() {
        return cancelHref;
    }

    @Override
    protected CancelRoomResponse parseResponse(String data) {
        return CancelRoomResponse.of(data);
    }

    /** 错误体需要解析，否则无法区分「已取消/不存在」与「取消本身失败」 */
    @Override
    public boolean isParseError() {
        return true;
    }
}
