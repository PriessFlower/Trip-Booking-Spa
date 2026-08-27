package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code taobao.xhotel.distribution.ari.availability} 响应（simplify 形态）。
 * 字段树见 docs/fliggy/distribution-api.md §2。价格单位=分、币种字段自带。
 */
public class FliggyAriResponse extends FliggyTopResponse {

    public static FliggyAriResponse parse(String raw) {
        FliggyAriResponse r = new FliggyAriResponse();
        r.init(raw);
        return r;
    }

    @Override
    protected String rootKey() {
        return "xhotel_distribution_ari_availability_response";
    }

    private JsonNode data() {
        return payload == null ? null : payload.get("data");
    }

    /** 定价策略 key，验价必须回传（与 rate_key 配对，身份与令牌永不同字段的又一例证） */
    public String requestTraceId() {
        return text(data(), "request_trace_id");
    }

    /** 全部报价行（跨酒店摊平；本仓按单店查询，通常只有一家）。无数据返回空列表 */
    public List<JsonNode> rates() {
        List<JsonNode> out = new ArrayList<>();
        JsonNode data = data();
        JsonNode properties = data == null ? null : data.get("properties");
        if (properties == null || !properties.isArray()) {
            return out;
        }
        for (JsonNode property : properties) {
            JsonNode rates = property.get("rates");
            if (rates != null && rates.isArray()) {
                rates.forEach(out::add);
            }
        }
        return out;
    }

    @Override
    public boolean isSucc() {
        return !isPlatformError() && data() != null;
    }

    /** 答了但没有任何报价：与「没问出结果」分开（B7 三态可辨） */
    @Override
    public boolean isEmptyResult() {
        return isSucc() && rates().isEmpty();
    }

    /**
     * 该酒店已被飞猪下架（不在我们账号的可售清单）。形态是平台层
     * {@code FAIL_BIZ_DEPENDENCY_ERROR + "hids is empty"}——语义来自 cursor 生产实证
     * （FliggyPriceFetchService「供应商已将资源下架」按空结果处理，跑了数月），
     * 本仓 2026-08-26 首笔真实调用同形复现。这是「明确无货」不是「没问出来」，
     * 折进不确定会让下架店被无限重试。
     */
    public boolean isHotelDelisted() {
        String error = platformError();
        return error != null && error.contains("hids is empty");
    }
}
