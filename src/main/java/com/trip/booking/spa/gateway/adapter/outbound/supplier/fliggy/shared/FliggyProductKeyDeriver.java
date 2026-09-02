package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.CancelClassifier;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞猪的餐食/退改归一化与身份派生（取值语义见 docs/fliggy/distribution-api.md §2）。
 * 解析不出的一律 UNKNOWN——可售不进目录（R-5.4/R-1.6），不把「没看懂」说成已知。
 */
@Slf4j
@Component
public class FliggyProductKeyDeriver {

    private final FliggyProperties properties;

    public FliggyProductKeyDeriver(FliggyProperties properties) {
        this.properties = properties;
    }

    /** type 2（两餐）返回 null（餐食未知）：官方没说是哪两餐，实测确认前不猜 */
    public Meal convertMeal(JsonNode meals) {
        if (meals == null || meals.isNull()) {
            return null;
        }
        int type = meals.path("type").asInt(-1);
        int number = Math.max(meals.path("number").asInt(0), 0);
        Meal meal = new Meal();
        switch (type) {
            case 0:
                meal.count = 0;
                meal.lunchCount = 0;
                meal.dinnerCount = 0;
                return meal;
            case 1:
                meal.count = number > 0 ? number : 1;
                meal.lunchCount = 0;
                meal.dinnerCount = 0;
                return meal;
            case 3:
                meal.count = number > 0 ? number : 1;
                meal.lunchCount = meal.count;
                meal.dinnerCount = meal.count;
                return meal;
            default:
                return null;
        }
    }

    /** 规则时刻的时区（GMT+8）。实证：响应 time 毫秒戳=首段 onward 的北京时间——东京店
     * 却非东京时间（守护钉在 FliggyRealPayloadTest），即各段时刻已换算为北京时间表达 */
    private static final java.time.ZoneId RULE_ZONE = java.time.ZoneId.of("Asia/Shanghai");
    private static final java.time.format.DateTimeFormatter RULE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 罚金为 0 的段=免费取消窗，非零段=扣固定金额（分）；{@code before}=该段截止时刻距
     * 入住日 24:00（北京时间）的小时数。任一段缺金额或时刻解析失败，整体返回空列表
     * （=UNKNOWN）——半份条款比没有更误导（同艺龙 convertLadder 口径）。
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, JsonNode cancelPolicy) {
        List<CancelPolicy> out = new ArrayList<>();
        JsonNode rules = cancelPolicy == null ? null : cancelPolicy.get("rules");
        if (rules == null || !rules.isArray()) {
            return out;
        }
        for (JsonNode rule : rules) {
            // 真实报文里 inclusive_amount 是数字串（"10524"），canConvertToInt 对文本节点
            // 恒 false——用它判会把全部规则丢光，cancelClass 恒 UNKNOWN（进不了目录，
            // 上游还按"退改从严"兜底）。按数字与数字串两种形态解析
            Integer cents = centsOrNull(rule.get("inclusive_amount"));
            JsonNode end = rule.get("before");
            Integer before = end == null || end.isNull() ? null
                    : hoursUntilCheckInEnd(end.asText(), checkIn);
            if (cents == null || before == null) {
                log.info("飞猪退改规范化：规则缺金额或时刻，整体按 UNKNOWN 处理(R-5.4),rule={}", rule);
                return new ArrayList<>();
            }
            out.add(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT+08:00")
                    .before(before)
                    .type(cents == 0 ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                    .amount(cents)
                    .build());
        }
        // 过期段不许流出：截止时刻已过的段行使不了，既不能对外承诺，也不该参与判类
        // （2026-09-02 实测艺龙 14.3%、飞猪 4.4% 的"可免费取消"其实免费窗早已关闭）
        return CancelClassifier.liveSegments(out, checkIn, java.time.Instant.now());
    }

    /** 某时刻距「入住日 24:00」的小时数（下限 25，字段约定必须>24）。基准恒按北京时间——
     * 生产容器跑 UTC，用服务器时区会把"还能免费取消多久"说长 8 小时（艺龙同款教训） */
    private static Integer hoursUntilCheckInEnd(String ruleEnd, String checkIn) {
        try {
            java.time.Instant end = java.time.LocalDateTime.parse(ruleEnd, RULE_TIME)
                    .atZone(RULE_ZONE).toInstant();
            java.time.Instant checkInEnd = java.time.LocalDate.parse(checkIn)
                    .plusDays(1).atStartOfDay(RULE_ZONE).toInstant();
            return Math.max(25, (int) Math.ceil(
                    java.time.Duration.between(end, checkInEnd).toMinutes() / 60.0));
        } catch (Exception e) {
            return null;
        }
    }

    /** 成分一次算出（R-2.8），账号成分=appKey */
    public ProductIdentity deriveIdentity(String supplierHotelId, String roomId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.count), isPositive(meal.lunchCount),
                        isPositive(meal.dinnerCount));
        return ProductIdentity.of(SupplierSourceEnum.FLIGGY.getCode(), properties.getAppKey(),
                supplierHotelId, roomId, mealSignature, classifyCancel(cancelPolicy, totalCents), occupancy);
    }

    /** 退改三分类：判据在 {@link CancelClassifier}（全仓唯一）。过期段已在归一化时滤掉 */
    private static CancelClass classifyCancel(List<CancelPolicy> cancelPolicy, Integer totalCents) {
        return CancelClassifier.classify(cancelPolicy, null, totalCents, null);
    }

    private static boolean isPositive(Integer v) {
        return v != null && v > 0;
    }

    private static Integer centsOrNull(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return Integer.parseInt(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
