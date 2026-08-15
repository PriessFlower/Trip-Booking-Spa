package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductKeyFactory;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 艺龙产品规范化与键派生的<b>唯一权威</b>（docs/product-identity.md R-1.1），
 * 阶段4 移植标准的"适配层两钩子"实物：餐食/退改规范化 + 键派生。
 * 查价响应组装与验价 resolve 匹配都必须经由本类——键分叉即身份分叉。
 *
 * <p>键成分：supplier=ELONG、账号=艺龙账户名（单账号；马甲是产品维促销凭证而非
 * 账号维，不进键）、supplierRoomId=<b>RatePlan.RoomTypeId</b>（cursor 全程以其为
 * 房型等价锚；不是外层 Room.RoomId）、餐食、退改类、占用。
 *
 * <p>规范化纪律（R-5.4）：餐食/退改解析不出一律 UNKNOWN，禁止兜成任何确定值。
 * UNKNOWN 键可在实时链路流转，但不进目录。cursor 把"解析不出"兜成硬不可退是
 * 防赔款的故意设计——SPA 侧用 UNKNOWN 不进目录达成同等防护而不说谎。
 */
@Slf4j
@Component
public class ElongProductKeyDeriver {

    @Resource
    private ElongProperties properties;

    /** 仅供测试构造场景使用 */
    public void setProperties(ElongProperties properties) {
        this.properties = properties;
    }

    public String deriveProductKey(String supplierHotelId, String roomTypeId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.getCount()), isPositive(meal.getLunchCount()), isPositive(meal.getDinnerCount()));
        CancelClass cancelClass;
        if (CollectionUtils.isEmpty(cancelPolicy)) {
            cancelClass = CancelClass.UNKNOWN;
        } else if (cancelPolicy.stream().anyMatch(p -> Integer.valueOf(1).equals(p.getCancelType())
                && RefundType.NO_DEDUCTION == p.getType())) {
            // 存在免费取消窗口（R-5.1 的 FREE 判据），罚金阶梯照常跟在后面
            cancelClass = CancelClass.FREE_CANCELLABLE;
        } else if (cancelPolicy.stream().allMatch(p -> Integer.valueOf(0).equals(p.getCancelType()))) {
            cancelClass = CancelClass.NON_REFUNDABLE;
        } else {
            // 可取消但全程收费：既非"有免费窗口"也非"全程不可退"，三分类无处安放。
            // 按元规则 R-1.6（赌错只许少卖）归 UNKNOWN——实时可售，不进目录
            cancelClass = CancelClass.UNKNOWN;
        }
        return ProductKeyFactory.derive(SupplierSourceEnum.ELONG.getCode(), properties.getUser(),
                supplierHotelId, roomTypeId, mealSignature, cancelClass, occupancy);
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }

    /**
     * 餐食规范化：{@code meals.dayMealTable[].breakfastShare} 取<b>最大值</b>
     * （逐日份数，入住当日常为 0，取最大才是该产品的真实早餐配置——cursor 同口径）。
     * dayMealTable 缺席时回退历史字段 NightlyRates[].BreakfastCount（同样取最大）。
     * 两处都解析不出返回 null → MealSignature.unknown() → 不进目录（R-5.4）。
     *
     * <p>艺龙预付产品无午/晚餐概念，lunch/dinner 恒 0。
     */
    public Meal convertMeal(ElongRatePlan plan) {
        Integer breakfast = maxBreakfastShare(plan.getMeals());
        if (breakfast == null) {
            breakfast = maxBreakfastCount(plan.getNightlyRates());
        }
        if (breakfast == null) {
            log.info("艺龙餐食规范化：meals.dayMealTable 与 NightlyRates.BreakfastCount 均缺席，按 UNKNOWN 处理(R-5.4),ratePlanId={},goodsUniqId={}",
                    plan.getRatePlanId(), plan.getGoodsUniqId());
            return null;
        }
        String desc = plan.getMeals() != null && plan.getMeals().hasNonNull("mealCopyWriting")
                ? plan.getMeals().get("mealCopyWriting").asText() : (breakfast > 0 ? "含早餐" : "");
        return Meal.builder().count(breakfast).lunchCount(0).dinnerCount(0).mealDesc(desc).build();
    }

    private static Integer maxBreakfastShare(JsonNode meals) {
        if (meals == null || !meals.has("dayMealTable") || !meals.get("dayMealTable").isArray()
                || meals.get("dayMealTable").isEmpty()) {
            return null;
        }
        int max = 0;
        for (JsonNode day : meals.get("dayMealTable")) {
            if (!day.has("breakfastShare") || !day.get("breakfastShare").canConvertToInt()) {
                return null;
            }
            max = Math.max(max, day.get("breakfastShare").asInt());
        }
        return max;
    }

    private static Integer maxBreakfastCount(List<ElongNightlyRate> nightlyRates) {
        if (CollectionUtils.isEmpty(nightlyRates)) {
            return null;
        }
        Integer max = null;
        for (ElongNightlyRate nightly : nightlyRates) {
            if (nightly.getBreakfastCount() != null) {
                max = max == null ? nightly.getBreakfastCount() : Math.max(max, nightly.getBreakfastCount());
            }
        }
        return max;
    }

    /**
     * 退改规范化：解析 hotel.detail 的 PrepayResult 阶梯（LadderParseList）。
     *
     * <p>输出三种形态，消费方按 {@link #deriveProductKey} 的口径归类：
     * <ul>
     *   <li>明确不可取消（CancelType∈{0,4} 或 CancelTag 含"不可取消"）→ 单条 cancelType=0
     *       → NON_REFUNDABLE</li>
     *   <li>明确可退（CancelTag 含"免费取消"/"限时取消"）且阶梯完整可解析 → cancelType=1
     *       条目 → FREE_CANCELLABLE</li>
     *   <li>其余（PrepayResult 缺席——hotel.detail 常态、字段不全、CutType 未知、
     *       金额缺 AmountRmb）→ 空列表 → UNKNOWN，不进目录（R-5.4）。真实退改以验价
     *       响应 interValidateInfo.CancelPolicyList 为准</li>
     * </ul>
     *
     * <p>阶梯字段（官方文档 hotel.detail「LadderParse 节点」，2026-08-15 核对；实测样本
     * CutValue 53.72 × 夜价 ≈ AmountRmb 263.3 反算吻合）：BeginTime/EndTime 为 Unix 秒；
     * <b>CutType 0=不扣费/1=金额/2=比例/3=首晚房费/4=全额房费</b>——注意 cursor
     * ElongCancelRuleParser 的旧表（3=百分比/5=扣几晚）与官方文档冲突，以官方为准。
     * 金额严格取 AmountRmb，缺失即整条视为解析失败，禁止拿合约币种 Amount 冒充人民币。
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, JsonNode prepayResult) {
        if (prepayResult == null || prepayResult.isNull() || prepayResult.isEmpty()) {
            return List.of();
        }
        String cancelTag = prepayResult.hasNonNull("CancelTag") ? prepayResult.get("CancelTag").asText() : "";
        Integer cancelType = prepayResult.hasNonNull("CancelType") ? prepayResult.get("CancelType").asInt() : null;
        if (cancelTag.contains("不可取消") || Integer.valueOf(0).equals(cancelType) || Integer.valueOf(4).equals(cancelType)) {
            List<CancelPolicy> nonRefundable = new ArrayList<>();
            nonRefundable.add(CancelPolicy.builder().cancelType(0).type(RefundType.NO_CANCEL).build());
            return nonRefundable;
        }
        // 阶梯本身语义自足（官方 CutType 表），不以 CancelTag 文案为解析前提——
        // 实测存在"订单确认后，北京时间…前取消收取￥…"等表外文案，阶梯照样完整
        JsonNode ladders = firstArray(prepayResult, "LadderParseList", "LadderCancel", "CancelRules");
        if (ladders == null) {
            log.info("艺龙退改规范化：缺阶梯明细，按 UNKNOWN 处理(R-5.4),cancelTag={},cancelType={}", cancelTag, cancelType);
            return List.of();
        }
        List<CancelPolicy> policies = new ArrayList<>();
        for (JsonNode ladder : ladders) {
            CancelPolicy policy = convertLadder(checkIn, ladder);
            if (policy == null) {
                log.info("艺龙退改规范化：阶梯不完整或 CutType 未知，整体按 UNKNOWN 处理(R-5.4),ladder={}", ladder);
                return List.of();
            }
            policies.add(policy);
        }
        return policies;
    }

    /** 单条阶梯 → CancelPolicy；缺时间、缺 AmountRmb、CutType 未知一律返回 null（解析失败） */
    private CancelPolicy convertLadder(String checkIn, JsonNode ladder) {
        if (!ladder.hasNonNull("EndTime") || !ladder.hasNonNull("CutType")) {
            return null;
        }
        int before;
        String timeZone = "GMT+08:00";
        try {
            Date end = new Date(ladder.get("EndTime").asLong() * 1000L);
            Date checkInEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(checkIn + " 24:00:00");
            before = Math.max(25, DateUtil.diffHour(end, checkInEnd));
        } catch (Exception e) {
            log.info("艺龙退改规范化：阶梯时间无法解析,endTime={},checkIn={}", ladder.get("EndTime"), checkIn);
            return null;
        }
        // CutType 取值以官方文档为准（0=不扣费/1=金额/2=比例/3=首晚房费/4=全额房费），
        // 表外取值一律解析失败（R-5.4），禁止兜底
        switch (ladder.get("CutType").asInt(-1)) {
            case 0:
                return CancelPolicy.builder().cancelType(1).timeZone(timeZone).before(before)
                        .type(RefundType.NO_DEDUCTION).build();
            case 1:
                if (!ladder.hasNonNull("AmountRmb")) {
                    return null;
                }
                return CancelPolicy.builder().cancelType(1).timeZone(timeZone).before(before)
                        .type(RefundType.DEDUCT_BY_AMOUNT).value(ladder.get("AmountRmb").asDouble()).build();
            case 2:
                if (!ladder.hasNonNull("CutValue")) {
                    return null;
                }
                return CancelPolicy.builder().cancelType(1).timeZone(timeZone).before(before)
                        .type(RefundType.DEDUCT_BY_PERCENT).value(ladder.get("CutValue").asDouble()).build();
            case 3:
                return CancelPolicy.builder().cancelType(1).timeZone(timeZone).before(before)
                        .type(RefundType.DEDUCT_FIRST_NIGHT).build();
            case 4:
                return CancelPolicy.builder().cancelType(1).timeZone(timeZone).before(before)
                        .type(RefundType.DEDUCT_BY_PERCENT).value(100D).build();
            default:
                return null;
        }
    }

    private static JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isArray() && !child.isEmpty()) {
                return child;
            }
        }
        return null;
    }
}
