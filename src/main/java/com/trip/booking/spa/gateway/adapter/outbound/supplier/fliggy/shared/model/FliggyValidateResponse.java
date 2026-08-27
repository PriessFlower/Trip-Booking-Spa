package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;

/**
 * {@code taobao.xhotel.order.international.distribution.validate} 响应（simplify 形态）。
 * 字段树见 docs/fliggy/distribution-api.md §3。
 */
public class FliggyValidateResponse extends FliggyTopResponse {

    public static FliggyValidateResponse parse(String raw) {
        FliggyValidateResponse r = new FliggyValidateResponse();
        r.init(raw);
        return r;
    }

    @Override
    protected String rootKey() {
        return "xhotel_order_international_distribution_validate_response";
    }

    /** 业务层是否成功：is_success 且 result 在场。业务失败的码义未核实,一律回不确定 */
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

    /** 创单必须的第二把钥匙 */
    public String createKey() {
        return text(result(), "create_key");
    }

    public JsonNode ratePlanInfo() {
        JsonNode r = result();
        return r == null ? null : r.get("rate_plan_info");
    }

    /** 验价确认的总房价（分）；解析不到返回 null——价格不许猜 */
    public Integer totalRoomPriceCents() {
        JsonNode info = ratePlanInfo();
        JsonNode v = info == null ? null : info.get("total_room_price");
        return v == null || !v.canConvertToInt() ? null : v.asInt();
    }

    /** 币种（cursor 实证 USD，勿假设 CNY） */
    public String currencyCode() {
        return text(ratePlanInfo(), "currency_code");
    }

    /** 业务层错误码（is_success=false 时），进日志——码义未核实不进判定 */
    public String bizErrorCode() {
        return payload == null ? null : text(payload, "error_resp_code");
    }

    /** 平台码优先，业务层失败时补业务码（biz: 前缀区分两层，码位不混装） */
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
