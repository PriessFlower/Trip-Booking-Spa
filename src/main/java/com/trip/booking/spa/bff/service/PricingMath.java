package com.trip.booking.spa.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

/**
 * BP5「将 tax_and_service_fee + property_fee 合并显示为税费」的唯一计算点。
 *
 * <p>对 occupancy_pricing 的 nightly 与 stay 中所有非房费项（type 不是 base_rate /
 * extra_person_fee）按 BigDecimal 精确加总，不舍入、不换币种——结果 scale 即原始
 * 字符串相加的自然 scale。除此之外的任何金额一律原样透传，禁止在 UI 或本层再算。
 */
final class PricingMath {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PricingMath() {
    }

    /**
     * @param pricing 单个 occupancy 的 pricing 节点（含 nightly/stay/totals）
     * @return {value, currency}；无税费项或币种不一致时返回 null（宁缺毋错）
     */
    static ObjectNode taxesAndFees(JsonNode pricing) {
        if (pricing == null || pricing.isMissingNode()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        boolean found = false;
        for (JsonNode night : pricing.path("nightly")) {
            for (JsonNode charge : night) {
                String type = charge.path("type").asText("");
                if ("base_rate".equals(type) || "extra_person_fee".equals(type)) {
                    continue;
                }
                String value = charge.path("value").asText(null);
                String chargeCurrency = charge.path("currency").asText(null);
                if (value == null || chargeCurrency == null) {
                    continue;
                }
                if (currency == null) {
                    currency = chargeCurrency;
                } else if (!currency.equals(chargeCurrency)) {
                    return null; // 币种不一致时不合并，前端回退逐项展示
                }
                total = total.add(new BigDecimal(value));
                found = true;
            }
        }
        for (JsonNode charge : pricing.path("stay")) {
            String type = charge.path("type").asText("");
            if ("base_rate".equals(type) || "extra_person_fee".equals(type)) {
                continue;
            }
            String value = charge.path("value").asText(null);
            String chargeCurrency = charge.path("currency").asText(null);
            if (value == null || chargeCurrency == null) {
                continue;
            }
            if (currency == null) {
                currency = chargeCurrency;
            } else if (!currency.equals(chargeCurrency)) {
                return null;
            }
            total = total.add(new BigDecimal(value));
            found = true;
        }
        if (!found) {
            return null;
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.put("value", total.toPlainString());
        result.put("currency", currency);
        return result;
    }
}
