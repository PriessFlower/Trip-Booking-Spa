package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;

/**
 * {@code taobao.xhotel.order.international.distribution.create} 响应
 * （docs/fliggy/distribution-api.md §4）：{@code result.tid} 即飞猪订单号。
 */
public class FliggyCreateResponse extends FliggyTopResponse {

    public static FliggyCreateResponse parse(String raw) {
        FliggyCreateResponse r = new FliggyCreateResponse();
        r.init(raw);
        return r;
    }

    @Override
    protected String rootKey() {
        return "xhotel_order_international_distribution_create_response";
    }

    @Override
    public boolean isSucc() {
        return !isPlatformError() && payload != null
                && payload.path("is_success").asBoolean(false)
                && fliggyOrderId() != null;
    }

    /** 飞猪订单号（tid）。拿到它才算「确证已成单」 */
    public String fliggyOrderId() {
        var result = payload == null ? null : payload.get("result");
        var tid = result == null ? null : result.get("tid");
        return tid == null || tid.isNull() ? null : tid.asText();
    }

    public String bizErrorCode() {
        return payload == null ? null : text(payload, "error_resp_code");
    }

    @Override
    public String metricErrorCode() {
        String platform = super.metricErrorCode();
        if (platform != null) {
            return platform;
        }
        String biz = bizErrorCode();
        return biz == null ? null : "biz:" + biz;
    }
}
