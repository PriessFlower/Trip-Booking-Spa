package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;

/**
 * {@code taobao.xhotel.order.international.distribution.detail} 响应
 * （docs/fliggy/distribution-api.md §5）。{@code order_status} 的取值枚举官方未列
 * （必测清单第 3 项）——映射不出的状态一律回 null，绝不猜。
 */
public class FliggyOrderDetailResponse extends FliggyTopResponse {

    public static FliggyOrderDetailResponse parse(String raw) {
        FliggyOrderDetailResponse r = new FliggyOrderDetailResponse();
        r.init(raw);
        return r;
    }

    @Override
    protected String rootKey() {
        return "xhotel_order_international_distribution_detail_response";
    }

    @Override
    public boolean isSucc() {
        return !isPlatformError() && payload != null
                && payload.path("is_success").asBoolean(false)
                && result() != null;
    }

    public JsonNode result() {
        JsonNode r = payload == null ? null : payload.get("result");
        return r == null || r.isNull() ? null : r;
    }

    private JsonNode baseInfo() {
        JsonNode r = result();
        return r == null ? null : r.get("order_base_info");
    }

    public String orderStatus() {
        return text(baseInfo(), "order_status");
    }

    public String orderStatusDesc() {
        return text(baseInfo(), "order_status_desc");
    }

    /** 供应商侧确认号（酒店确认码） */
    public String confirmCode() {
        JsonNode r = result();
        JsonNode fulfill = r == null ? null : r.get("order_fulfill_info");
        return text(fulfill, "out_confirm_code");
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
