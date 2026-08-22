package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.ProductKeyFactory;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

    /** 艺龙全量时刻以北京时间报出（阶梯 EndTime、CancelPolicyList 的 DateTo 均然） */
    private static final ZoneOffset ELONG_ZONE = ZoneOffset.ofHours(8);

    @Resource
    private ElongProperties properties;

    /** 仅供测试构造场景使用 */
    public void setProperties(ElongProperties properties) {
        this.properties = properties;
    }

    /**
     * 派生身份<b>及其全部成分</b>（R-2.8：成分只算一次，下游照抄）。
     *
     * <p>建档要落的 {@code meal_signature}/{@code cancel_class}/{@code occupancy} 都从这里取，
     * 不许拿 {@link Meal}/{@link CancelPolicy} 再判一遍——重判必然降维且会与本方法分叉。
     */
    public ProductIdentity deriveIdentity(String supplierHotelId, String roomTypeId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.getCount()), isPositive(meal.getLunchCount()), isPositive(meal.getDinnerCount()));
        CancelClass cancelClass = classifyCancel(cancelPolicy);
        return ProductIdentity.of(SupplierSourceEnum.ELONG.getCode(), properties.getUser(),
                supplierHotelId, roomTypeId, mealSignature, cancelClass, occupancy);
    }

    /** 只要 key 不要成分时用（如 resolve 匹配只做键比对） */
    public String deriveProductKey(String supplierHotelId, String roomTypeId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy) {
        return deriveIdentity(supplierHotelId, roomTypeId, meal, cancelPolicy, occupancy).productKey();
    }

    /** 退改三分类。抽出以便建档判定（{@link #isCatalogEligible}）复用同一判据，杜绝两处漂移。 */
    private static CancelClass classifyCancel(List<CancelPolicy> cancelPolicy) {
        if (CollectionUtils.isEmpty(cancelPolicy)) {
            return CancelClass.UNKNOWN;
        }
        if (cancelPolicy.stream().anyMatch(p -> Integer.valueOf(1).equals(p.getCancelType())
                && RefundType.NO_DEDUCTION == p.getType())) {
            // 存在免费取消窗口（R-5.1 的 FREE 判据），罚金阶梯照常跟在后面
            return CancelClass.FREE_CANCELLABLE;
        }
        if (cancelPolicy.stream().allMatch(p -> Integer.valueOf(0).equals(p.getCancelType()))) {
            return CancelClass.NON_REFUNDABLE;
        }
        // 可取消但全程收费：既非"有免费窗口"也非"全程不可退"，三分类无处安放。
        // 按元规则 R-1.6（赌错只许少卖）归 UNKNOWN——实时可售，不进目录
        return CancelClass.UNKNOWN;
    }

    /**
     * 该产品的餐食与退改是否<b>全部解析成功</b>——即是否可进目录（R-5.4）。
     *
     * <p>判据必须与 {@link #deriveProductKey} 同源:UNKNOWN 会正常参与 key 派生
     * （key 里带 {@code m:UNKNOWN} / {@code c:UNKNOWN}，是合法取值，实时链路照常可售），
     * 但<b>不得进目录</b>——目录里的 UNKNOWN 会与"已知不含早/已知不可退"混为一谈，
     * 污染等价类查询。故建档侧不得自行重写判据（第三种 UNKNOWN——"可取消但全程收费"
     * ——从出参 DTO 根本看不出来），只能问这里。
     */
    public boolean isCatalogEligible(Meal meal, List<CancelPolicy> cancelPolicy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.getCount()), isPositive(meal.getLunchCount()),
                        isPositive(meal.getDinnerCount()));
        return mealSignature.isKnown() && classifyCancel(cancelPolicy) != CancelClass.UNKNOWN;
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }

    /** 餐食形态。{@code null} 表示无法归类，一律按 UNKNOWN 处理（R-5.4），绝不猜。 */
    private enum MealShape {
        /** 供应商正面声明「无餐食」——这是可信的确定信息，不是"没填" */
        NONE(false, false, false),
        BREAKFAST(true, false, false),
        BREAKFAST_DINNER(true, false, true),
        BREAKFAST_LUNCH_DINNER(true, true, true),
        /** 有餐，但到店协商才定是哪几顿——订时不可确定，按 UNKNOWN */
        INDETERMINATE(false, false, false);

        final boolean breakfast;
        final boolean lunch;
        final boolean dinner;

        MealShape(boolean breakfast, boolean lunch, boolean dinner) {
            this.breakfast = breakfast;
            this.lunch = lunch;
            this.dinner = dinner;
        }
    }

    /**
     * 餐食规范化：以 {@code meals.mealText}（艺龙的餐食文案）判定餐食<b>形态</b>，
     * 以 {@code meals.dayMealTable[].breakfastShare} 判定<b>份数</b>。
     *
     * <p><b>为什么形态不能只看 breakfastShare</b>（2026-08-19 生产实证，828 家酒店 30,243 条报价）：
     * 艺龙的午/晚餐信息<b>不在</b>结构化字段里，只在文案里；而 breakfastShare 对
     * 「到店三选二」这类产品报 0——它报的是"早餐不保证"，不是"没有餐"。旧实现把
     * lunch/dinner 写死 0、只读 breakfastShare，导致约 1.01%（306 条）餐食签名判错：
     * <ul>
     *   <li>260 条「N份早餐或N份午餐或N份晚餐(到店3选2)」被判成无餐，<b>与「无餐食」合流</b>；</li>
     *   <li>46 条「N份早餐和N份午餐和N份晚餐」（全餐）被判成仅含早，<b>与纯含早合流</b>。</li>
     * </ul>
     * 后果与 Expedia 2026-08-19 那次同构（见 ExpediaProductKeyDeriver）：裁剪按等价类留最低价，
     * 含餐多的那条被当同一卖法裁掉（F-3.2）；resolve 硬门是"键相等"，键分不出餐食就可能
     * 拿无餐票换含餐票（R-3.2）。
     *
     * <p><b>文案取值是模板生成的、可枚举的</b>（实测 19 种，归一份数后 9 种），故按结构归类而非
     * 自由文本解析。归类不出的一律 UNKNOWN 并打日志，让表能长——沿用 Expedia 那次 2203 的
     * 判据：猜错是<b>卖错</b>，不归类最多<b>少卖</b>（R-1.6 取后者）。
     */
    public Meal convertMeal(ElongRatePlan plan) {
        String copy = mealText(plan);
        MealShape shape = classifyMealText(copy);
        if (shape == null) {
            log.info("艺龙餐食规范化：餐食文案无法归类，按 UNKNOWN 处理(R-5.4)，copy=[{}],ratePlanId={},goodsUniqId={}",
                    copy, plan.getRatePlanId(), plan.getGoodsUniqId());
            return null;
        }
        if (shape == MealShape.INDETERMINATE) {
            log.info("艺龙餐食规范化：餐食组合需到店协商、订时不可确定，按 UNKNOWN 处理(R-5.4)，copy=[{}],ratePlanId={}",
                    copy, plan.getRatePlanId());
            return null;
        }

        // 份数：结构化字段优先（逐日取最大，入住当日常为 0）；缺失时退回文案里的「N份」
        Integer share = maxBreakfastShare(plan.getMeals());
        if (share == null) {
            share = maxBreakfastCount(plan.getNightlyRates());
        }
        if (share == null) {
            share = sharesInText(copy);
        }
        int portions = share == null ? 1 : share;
        return Meal.builder()
                .count(shape.breakfast ? Math.max(portions, 1) : 0)
                .lunchCount(shape.lunch ? Math.max(portions, 1) : 0)
                .dinnerCount(shape.dinner ? Math.max(portions, 1) : 0)
                .mealDesc(copy == null ? "" : copy)
                .build();
    }

    private static String mealText(ElongRatePlan plan) {
        JsonNode meals = plan.getMeals();
        return meals != null && meals.hasNonNull("mealText")
                ? meals.get("mealText").asText() : null;
    }

    /**
     * 餐食文案归类。返回 {@code null} = 无法归类（调用方按 UNKNOWN 处理）。
     *
     * <p><b>判定顺序是安全护栏，不得调整</b>：「或/选」必须最先判。任何"到店协商"的措辞
     * 都不允许被后续分支读成确定值——这是本方法唯一会造成<b>卖错</b>的方向。
     */
    private static MealShape classifyMealText(String copy) {
        if (StringUtils.isBlank(copy)) {
            return null;   // 文案缺席：午/晚餐无从判断，不可假定为无
        }
        String t = copy.replaceAll("\\s+", "");
        // ① 选择型：到店 N 选 M，订时不可确定
        if (t.contains("或") || t.contains("选")) {
            return MealShape.INDETERMINATE;
        }
        // ② 供应商正面声明无餐
        if (t.contains("无餐食")) {
            return MealShape.NONE;
        }
        // ③ 确定型：以「和」并列，逐项认餐（"小食饮料"不计入三餐）
        boolean b = t.contains("早餐");
        boolean l = t.contains("午餐");
        boolean d = t.contains("晚餐");
        if (!b && !l && !d) {
            return null;   // 既非无餐、又不含任何餐名：形态不明
        }
        if (b && l && d) {
            return MealShape.BREAKFAST_LUNCH_DINNER;
        }
        if (b && !l && d) {
            return MealShape.BREAKFAST_DINNER;
        }
        if (b && !l && !d) {
            return MealShape.BREAKFAST;
        }
        // 仅午餐/仅晚餐/早+午 等组合：生产未见，不臆断（R-1.6）
        return null;
    }

    /** 从文案里取份数，如「2份早餐」→ 2。取不到返回 null。 */
    private static Integer sharesInText(String copy) {
        if (copy == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)份").matcher(copy);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
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

    /**
     * 验价响应的退改 → 契约条款。数据源 {@code interValidateInfo.ratePlanInfo.CancelPolicyList}，
     * <b>这是验价时点的权威条款</b>（查价的 PrepayResult 可能已过时）。
     *
     * <p>结构与查价侧完全不同，故单开一个方法（2026-08-17 抓取生产真实响应确认）：
     * <pre>
     * [{"Penalty":0.0,   "PenaltyRMB":0.0,   "DateFrom":"1970-01-01T00:00:00+08:00","DateTo":"2026-08-21T18:00:00+08:00"},
     *  {"Penalty":42.07, "PenaltyRMB":42.07, "DateFrom":"2026-08-21T18:00:00+08:00","DateTo":"2099-12-31T00:00:00+08:00"}]
     * </pre>
     * 语义是<b>时间段</b>而非阶梯点：在 {@code [DateFrom, DateTo)} 内取消收 {@code PenaltyRMB}。
     * 上例即"08-21 18:00 前免费，之后收 42.07 元"。
     *
     * <p>转换口径与 {@link #convertCancelPolicy} 保持一致：{@code before} = 该段截止时刻距
     * 入住日 24:00 的小时数（下限 25，同查价侧）；罚金为 0 记 {@link RefundType#NO_DEDUCTION}，
     * 否则记 {@link RefundType#DEDUCT_BY_AMOUNT}。末段 DateTo 恒为 2099（表示"此后一直"），
     * 算出的负值由下限兜住。
     *
     * <p>金额只认 {@code PenaltyRMB}：{@code Penalty} 是合约币种，跨币种直接当人民币用会算错
     * 罚金——与查价侧只认 {@code AmountRmb} 同一条纪律。
     *
     * @return 解析不出返回空列表（R-5.4），调用方不得据此声称"可免费取消"
     */
    public List<CancelPolicy> convertValidatedCancelPolicy(String checkIn, JsonNode cancelPolicyList) {
        if (cancelPolicyList == null || !cancelPolicyList.isArray() || cancelPolicyList.isEmpty()) {
            return List.of();
        }
        List<CancelPolicy> policies = new ArrayList<>();
        for (JsonNode seg : cancelPolicyList) {
            if (!seg.hasNonNull("PenaltyRMB") || !seg.hasNonNull("DateTo")) {
                log.info("艺龙验价退改：分段缺 PenaltyRMB/DateTo，整体按 UNKNOWN 处理(R-5.4),seg={}", seg);
                return List.of();
            }
            Integer before = hoursBeforeCheckIn(seg.get("DateTo").asText(), checkIn);
            if (before == null) {
                log.info("艺龙验价退改：DateTo 无法解析，整体按 UNKNOWN 处理(R-5.4),dateTo={}", seg.get("DateTo").asText());
                return List.of();
            }
            double penalty = seg.get("PenaltyRMB").asDouble();
            policies.add(penalty <= 0
                    ? CancelPolicy.builder().cancelType(1).timeZone("GMT+08:00").before(before)
                            .type(RefundType.NO_DEDUCTION).build()
                    : CancelPolicy.builder().cancelType(1).timeZone("GMT+08:00").before(before)
                            .type(RefundType.DEDUCT_BY_AMOUNT).value(penalty).build());
        }
        return policies;
    }

    /** ISO 带偏移时刻（如 2026-08-21T18:00:00+08:00）距入住日 24:00 的小时数；下限 25，同查价侧 */
    private static Integer hoursBeforeCheckIn(String isoInstant, String checkIn) {
        try {
            return hoursUntilCheckInEnd(OffsetDateTime.parse(isoInstant).toInstant(), checkIn);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 某时刻距「入住日 24:00」的小时数（下限 25）。
     *
     * <p><b>入住日 24:00 一律按 {@link #ELONG_ZONE} 解释，不用服务器时区</b>。
     * 艺龙的全部时刻（阶梯 EndTime、CancelPolicyList 的 DateTo）都以北京时间报出，
     * 而基准若随服务器时区漂移，同一份条款在不同机器上会算出不同的 before——
     * 生产容器实际跑在 UTC（2026-08-17 实测），偏 8 小时，会把"还能免费取消多久"
     * 说长，旅客据此在窗口外取消就要挨罚金。
     */
    private static int hoursUntilCheckInEnd(Instant at, String checkIn) {
        Instant checkInEnd = LocalDate.parse(checkIn).plusDays(1).atStartOfDay(ELONG_ZONE).toInstant();
        return Math.max(25, (int) Math.ceil(Duration.between(at, checkInEnd).toMinutes() / 60.0));
    }

    /** 单条阶梯 → CancelPolicy；缺时间、缺 AmountRmb、CutType 未知一律返回 null（解析失败） */
    private CancelPolicy convertLadder(String checkIn, JsonNode ladder) {
        if (!ladder.hasNonNull("EndTime") || !ladder.hasNonNull("CutType")) {
            return null;
        }
        int before;
        String timeZone = "GMT+08:00";
        try {
            // EndTime 是 Unix 秒（绝对时刻）；基准「入住日 24:00」按北京时间解释，
            // 理由见 hoursUntilCheckInEnd —— 用服务器时区会随部署环境漂移
            before = hoursUntilCheckInEnd(Instant.ofEpochSecond(ladder.get("EndTime").asLong()), checkIn);
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
