package com.trip.booking.spa.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BP5「将 tax_and_service_fee + property_fee 合并显示为税费」与多间房订单总额的唯一计算点。
 *
 * <p>对 occupancy_pricing 的 nightly 与 stay 中所有非房费项（type 不是 base_rate /
 * extra_person_fee）按 BigDecimal 精确加总，不舍入、不换币种——结果 scale 即原始
 * 字符串相加的自然 scale。除此之外的任何金额一律原样透传，禁止在 UI 或本层再算。
 */
final class PricingMath {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 需要跨间累加的 totals 字段。Rapid 的 occupancy_pricing 以 occupancy 串为键、值为
     * <b>单间</b>价格；多间同构时该 map 只有一个键，故订单总额必须按房间数逐间累加，
     * 不能直接取第一个值（那样 2 间会显示成 1 间的价）。
     */
    private static final List<String> SUMMABLE_TOTALS = List.of(
            "inclusive", "exclusive", "strikethrough", "inclusive_strikethrough",
            "property_fees", "property_inclusive", "property_inclusive_strikethrough",
            "marketing_fee", "gross_profit");

    /** totals 各字段下的币种节点 */
    private static final List<String> CURRENCY_NODES = List.of("request_currency", "billable_currency");

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

    /**
     * 多间房订单级聚合。每间按自己的 occupancy 串取 pricing（各间人数可不同，价格随之不同），
     * 再对 totals 与税费逐间 BigDecimal 累加。
     *
     * @param occupancyPricing Rapid 的 occupancy_pricing 节点（键为 occupancy 串）
     * @param occupancies      本次预订的每间 occupancy 串，<b>有重复即代表多间</b>
     * @return {roomCount, rooms:[{occupancy, totals, taxesAndFees}], totals, taxesAndFees}；
     *         任一间缺 pricing 时返回 null（宁缺毋错，前端回退到逐间展示）
     */
    static ObjectNode orderAggregate(JsonNode occupancyPricing, List<String> occupancies) {
        if (occupancyPricing == null || !occupancyPricing.isObject()
                || occupancies == null || occupancies.isEmpty()) {
            return null;
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.put("roomCount", occupancies.size());
        ArrayNode roomsOut = result.putArray("rooms");

        // 逐间累加：totals 各字段各币种一个累加器，键为 "字段/币种节点"
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, String> currencies = new LinkedHashMap<>();
        BigDecimal taxTotal = BigDecimal.ZERO;
        String taxCurrency = null;
        boolean taxUsable = true;

        for (String occupancy : occupancies) {
            JsonNode pricing = occupancyPricing.path(occupancy);
            if (pricing.isMissingNode() || !pricing.isObject()) {
                return null; // 该间没有对应报价，聚合结果不可信
            }
            ObjectNode roomOut = roomsOut.addObject();
            roomOut.put("occupancy", occupancy);
            roomOut.set("totals", pricing.path("totals"));

            ObjectNode roomTaxes = taxesAndFees(pricing);
            if (roomTaxes == null) {
                taxUsable = false;
            } else {
                roomOut.set("taxesAndFees", roomTaxes);
                String currency = roomTaxes.path("currency").asText();
                if (taxCurrency == null) {
                    taxCurrency = currency;
                } else if (!taxCurrency.equals(currency)) {
                    taxUsable = false;
                }
                taxTotal = taxTotal.add(new BigDecimal(roomTaxes.path("value").asText()));
            }

            JsonNode totals = pricing.path("totals");
            for (String field : SUMMABLE_TOTALS) {
                JsonNode amount = totals.path(field);
                if (amount.isMissingNode()) {
                    continue;
                }
                for (String node : CURRENCY_NODES) {
                    JsonNode money = amount.path(node);
                    String value = money.path("value").asText(null);
                    String currency = money.path("currency").asText(null);
                    if (value == null || currency == null) {
                        continue;
                    }
                    String key = field + "/" + node;
                    String seen = currencies.get(key);
                    if (seen == null) {
                        currencies.put(key, currency);
                    } else if (!seen.equals(currency)) {
                        currencies.put(key, null); // 币种不一致，该字段作废
                        continue;
                    } else if (seen.isEmpty()) {
                        continue;
                    }
                    sums.merge(key, new BigDecimal(value), BigDecimal::add);
                }
            }
        }

        ObjectNode totalsOut = result.putObject("totals");
        for (Map.Entry<String, BigDecimal> entry : sums.entrySet()) {
            String currency = currencies.get(entry.getKey());
            if (currency == null) {
                continue; // 跨间币种不一致的字段不下发
            }
            String[] parts = entry.getKey().split("/", 2);
            ObjectNode field = totalsOut.has(parts[0])
                    ? (ObjectNode) totalsOut.get(parts[0])
                    : totalsOut.putObject(parts[0]);
            ObjectNode money = field.putObject(parts[1]);
            money.put("value", entry.getValue().toPlainString());
            money.put("currency", currency);
        }
        if (taxUsable && taxCurrency != null) {
            ObjectNode taxes = result.putObject("taxesAndFees");
            taxes.put("value", taxTotal.toPlainString());
            taxes.put("currency", taxCurrency);
        }
        return result;
    }
}
