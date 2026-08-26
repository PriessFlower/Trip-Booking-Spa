package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
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
 * 飞猪的餐食/退改归一化与身份派生。取值语义来自 docs/fliggy/distribution-api.md §2：
 * {@code meals{number,type}}，type 0=无餐/1=含早/2=两餐/3=三餐；
 * {@code cancel_policy.rules[]{onward,before,inclusive_amount(分),currency}}。
 *
 * <p>纪律同两家先例：解析不出的一律 UNKNOWN（可售不进目录，R-5.4/R-1.6 赌错只许少卖），
 * 绝不把「没看懂」说成「已知不含早/已知不可退」。
 */
@Slf4j
@Component
public class FliggyProductKeyDeriver {

    private final FliggyProperties properties;

    public FliggyProductKeyDeriver(FliggyProperties properties) {
        this.properties = properties;
    }

    /**
     * type 2（两餐）返回 null（餐食未知）：官方没说是哪两餐，猜「早+晚」错配成本
     * 是 49046202 那类含早错配单——沙箱实测确认后再放进已知集。
     */
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

    /**
     * rules 里罚金为 0 的段视作免费取消窗口，非零段按扣固定金额（单位分）。
     * 时间字段的格式与时区官方未给样例（快照 §9 必测清单），MVP 只消费金额语义——
     * 免费窗的存在性足以判 {@link CancelClass}；时间细节待真实报文后补。
     * 解析不出任何段返回空列表（=UNKNOWN）。
     */
    public List<CancelPolicy> convertCancelPolicy(JsonNode cancelPolicy) {
        List<CancelPolicy> out = new ArrayList<>();
        JsonNode rules = cancelPolicy == null ? null : cancelPolicy.get("rules");
        if (rules == null || !rules.isArray()) {
            return out;
        }
        for (JsonNode rule : rules) {
            JsonNode amount = rule.get("inclusive_amount");
            if (amount == null || !amount.canConvertToInt()) {
                continue;
            }
            int cents = amount.asInt();
            out.add(CancelPolicy.builder()
                    .cancelType(1)
                    .type(cents == 0 ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                    .amount(cents)
                    .build());
        }
        return out;
    }

    /** 成分一次算出（R-2.8），账号成分=appKey */
    public ProductIdentity deriveIdentity(String supplierHotelId, String roomId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.count), isPositive(meal.lunchCount),
                        isPositive(meal.dinnerCount));
        return ProductIdentity.of(SupplierSourceEnum.FLIGGY.getCode(), properties.getAppKey(),
                supplierHotelId, roomId, mealSignature, classifyCancel(cancelPolicy), occupancy);
    }

    private static CancelClass classifyCancel(List<CancelPolicy> cancelPolicy) {
        if (CollectionUtils.isEmpty(cancelPolicy)) {
            return CancelClass.UNKNOWN;
        }
        if (cancelPolicy.stream().anyMatch(p -> RefundType.NO_DEDUCTION == p.getType())) {
            return CancelClass.FREE_CANCELLABLE;
        }
        // 全程收费:三分类无处安放,按 R-1.6 归 UNKNOWN(实时可售,不进目录)。
        // 飞猪没有显式的"不可退"标记(快照 §2),NON_REFUNDABLE 待真实报文给出判据
        return CancelClass.UNKNOWN;
    }

    private static boolean isPositive(Integer v) {
        return v != null && v > 0;
    }
}
