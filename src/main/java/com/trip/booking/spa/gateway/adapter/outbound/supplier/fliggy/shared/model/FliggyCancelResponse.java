package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;

/**
 * {@code taobao.xhotel.trade.international.distribution.cancel} 响应
 * （docs/fliggy/distribution-api.md §6）：{@code result{cancel_success, forfeit_fee}}。
 * forfeit_fee 官方未标币种（必测清单第 2 项），消费方自行处置。
 */
public class FliggyCancelResponse extends FliggyTopResponse {

    public static FliggyCancelResponse parse(String raw) {
        FliggyCancelResponse r = new FliggyCancelResponse();
        r.init(raw);
        return r;
    }

    @Override
    protected String rootKey() {
        return "xhotel_trade_international_distribution_cancel_response";
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

    /** 供应商明确的取消结论；解析不出返回 null（≠false——「没看懂」不是「被拒绝」） */
    public Boolean cancelSuccess() {
        JsonNode r = result();
        JsonNode v = r == null ? null : r.get("cancel_success");
        return v == null || !v.isBoolean() ? null : v.asBoolean();
    }

    /** 罚金（单位分，币种未标——cursor 实证按 USD，待沙箱确证）；缺席返回 null */
    public Integer forfeitFee() {
        JsonNode r = result();
        JsonNode v = r == null ? null : r.get("forfeit_fee");
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return Integer.parseInt(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
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
