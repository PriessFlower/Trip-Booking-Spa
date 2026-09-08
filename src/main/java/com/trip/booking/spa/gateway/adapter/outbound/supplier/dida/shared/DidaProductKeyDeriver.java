package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaCancellationPolicy;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.CancelClassifier;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 道旅产品规范化与键派生的<b>唯一权威</b>（docs/product-identity.md R-1.1）：
 * 餐食/退改规范化 + 键派生。查价组装与验价 resolve 匹配都必须经由本类——键分叉即身份分叉。
 *
 * <p>键成分：supplier=DIDA、账号=ClientID、supplierRoomId=<b>RatePlan.RoomTypeID</b>
 * （实测即道旅静态内容接口发布的房型主键，证据见 SupplierIdentityProfile.DIDA）、
 * 餐食、退改类、占用。RatePlanID 是易腐报价码，<b>不进键</b>。
 */
@Slf4j
@Component
public class DidaProductKeyDeriver {

    /** 道旅的取消政策时刻以北京时间报出（官方 price-search FromDate 字段说明，2026-09-08 查阅） */
    private static final ZoneId DIDA_ZONE = ZoneId.of("Asia/Shanghai");

    /** 实测的 MealType 取值：1=无餐、2=含早。官方无取值表，其余取值一律不猜（见 convertMeal） */
    private static final int MEAL_TYPE_NONE = 1;

    private static final int MEAL_TYPE_BREAKFAST = 2;

    @Resource
    private DidaProperties properties;

    /** 仅供测试构造场景使用 */
    public void setProperties(DidaProperties properties) {
        this.properties = properties;
    }

    /**
     * 派生身份<b>及其全部成分</b>（R-2.8：成分只算一次，下游照抄）。
     * 建档要落的 meal_signature/cancel_class/occupancy 都从这里取，不许再判一遍。
     */
    public ProductIdentity deriveIdentity(String supplierHotelId, String roomTypeId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(positive(meal.getCount()), positive(meal.getLunchCount()),
                        positive(meal.getDinnerCount()));
        CancelClass cancelClass = CancelClassifier.classify(cancelPolicy, null, totalCents, null);
        return ProductIdentity.of(SupplierSourceEnum.DIDA.getCode(), properties.getClientId(),
                supplierHotelId, roomTypeId, mealSignature, cancelClass, occupancy);
    }

    /** 只要 key 不要成分时用（resolve 匹配只做键比对） */
    public String deriveProductKey(String supplierHotelId, String roomTypeId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        return deriveIdentity(supplierHotelId, roomTypeId, meal, cancelPolicy, occupancy, totalCents).productKey();
    }

    /**
     * 餐食规范化：只认逐晚的 {@code MealType} + {@code MealAmount}（官方 price-search 注 8，
     * 2026-09-08 查阅：BreakfastType 已过时，且「人数跟餐数对不齐就会认为是无早」）。
     *
     * <p><b>官方没有给 MealType 的取值表</b>（字段说明只写着 "MealType"）。2026-09-08 生产
     * 实测 606 条报价，出现四种组合（BreakfastType, MealType, MealAmount）：
     * (1,1,0) 344 条、(2,2,2) 210 条、(2,3,2) 32 条、(2,7,2) 20 条。其中 MealType=7 的样例是
     * 日式「1泊2食」（一晚含两餐），而它的 BreakfastType 同样是 2——<b>照 BreakfastType 判就会
     * 把早+晚说成仅含早</b>，那是卖错。故只认两种有实证的取值：
     * <ul>
     *   <li>逐晚全部 {@code MealType=1} 且 {@code MealAmount=0} → 确定无餐</li>
     *   <li>逐晚全部 {@code MealType=2} 且 {@code MealAmount>0} → 确定含早（份数取逐晚最大）</li>
     *   <li>其余（含 3、7 及未来新值、逐晚不一致、字段缺失）→ UNKNOWN（R-5.4）</li>
     * </ul>
     * UNKNOWN 照常可售，只是不进产品目录；要收窄它须先向道旅要到 MealType 取值表。
     *
     * @return {@code null} 表示 UNKNOWN——调用方不得兜成任何确定值
     */
    public Meal convertMeal(DidaRatePlan plan) {
        List<DidaPriceItem> nights = plan.getPriceList();
        if (CollectionUtils.isEmpty(nights)) {
            return null;
        }
        boolean allNone = true;
        boolean allBreakfast = true;
        int portions = 0;
        for (DidaPriceItem night : nights) {
            Integer type = night.getMealType();
            int amount = night.getMealAmount() == null ? 0 : night.getMealAmount();
            allNone &= Integer.valueOf(MEAL_TYPE_NONE).equals(type) && amount == 0;
            allBreakfast &= Integer.valueOf(MEAL_TYPE_BREAKFAST).equals(type) && amount > 0;
            portions = Math.max(portions, amount);
        }
        if (allNone) {
            return Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        if (allBreakfast) {
            return Meal.builder().count(portions).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        log.info("道旅餐食规范化：MealType 组合无实证取值，按 UNKNOWN 处理(R-5.4),ratePlanId={},逐晚={}",
                plan.getRatePlanId(), mealDigest(nights));
        return null;
    }

    /**
     * 退改规范化：{@code RatePlanCancellationPolicyList}（查价）或 {@code CancellationPolicyList}
     * （验价）→ 契约条款。
     *
     * <p>语义（官方 price-search 字段说明，2026-09-08 查阅）：一条政策 = 「<b>从 FromDate 起</b>
     * 取消收 Amount」，FromDate 之前取消免费，时刻是<b>北京时间</b>。故本方法把「起始点列表」
     * 翻成本仓的「分段」形态：段 i 的截止时刻 = 下一条的 FromDate，末段无截止（按"此后一直"
     * 落 {@code before} 下限 25，与艺龙末段 2099 的处理同形）；首条 Amount>0 时补一段免费窗，
     * 其截止时刻即首条 FromDate。
     *
     * <p>金额币种即本次报价币种（{@code supplier.dida.currency}，默认 CNY）——道旅只给数值，
     * 不在这条列表里带币种。
     *
     * @return 解析不出返回空列表（R-5.4），调用方不得据此声称"可免费取消"
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<DidaCancellationPolicy> raw) {
        return convertCancelPolicy(checkIn, raw, Instant.now());
    }

    /**
     * 同上，但由调用方给时钟。{@code now} 决定哪些段已过期——过期判定是本方法的输出的一部分，
     * 把它留给 {@code Instant.now()} 就没法钉住"免费窗过期后不再对外承诺免费"这条判据。
     */
    List<CancelPolicy> convertCancelPolicy(String checkIn, List<DidaCancellationPolicy> raw, Instant now) {
        if (CollectionUtils.isEmpty(raw)) {
            // 政策列表缺席：此刻取消按什么罚无从判读。cursor 把它兜成"不可退"是防赔款的故意设计，
            // SPA 侧用 UNKNOWN 达成同等防护而不说谎（R-5.4）
            return List.of();
        }
        List<DidaCancellationPolicy> sorted = new ArrayList<>();
        for (DidaCancellationPolicy policy : raw) {
            if (policy == null || policy.getFromDate() == null || policy.getAmount() == null
                    || parseFromDate(policy.getFromDate()) == null) {
                log.info("道旅退改规范化：分段缺 FromDate/Amount 或时刻不可解析，整体按 UNKNOWN 处理(R-5.4),段={}",
                        policy == null ? null : policy.getFromDate());
                return List.of();
            }
            sorted.add(policy);
        }
        sorted.sort(Comparator.comparing(p -> parseFromDate(p.getFromDate())));

        List<CancelPolicy> policies = new ArrayList<>();
        BigDecimal firstAmount = sorted.get(0).getAmount();
        if (firstAmount.signum() > 0) {
            Integer before = hoursBeforeCheckInEnd(sorted.get(0).getFromDate(), checkIn);
            if (before == null) {
                return List.of();
            }
            policies.add(free(before));
        }
        for (int i = 0; i < sorted.size(); i++) {
            String deadline = i + 1 < sorted.size() ? sorted.get(i + 1).getFromDate() : null;
            Integer before = deadline == null ? MIN_BEFORE : hoursBeforeCheckInEnd(deadline, checkIn);
            if (before == null) {
                return List.of();
            }
            BigDecimal amount = sorted.get(i).getAmount();
            policies.add(amount.signum() <= 0 ? free(before)
                    : CancelPolicy.builder().cancelType(1).timeZone("GMT+08:00").before(before)
                            .type(RefundType.DEDUCT_BY_AMOUNT).value(amount.doubleValue()).build());
        }
        // 过期段不许流出：截止时刻已过的段行使不了，既不能对外承诺，也不该参与判类
        return CancelClassifier.liveSegments(policies, checkIn, now);
    }

    /** {@code before} 的下限，语义是"此后一直"（同 CancelPolicy#before 的 >24 约束） */
    private static final int MIN_BEFORE = 25;

    private static CancelPolicy free(int before) {
        return CancelPolicy.builder().cancelType(1).timeZone("GMT+08:00").before(before)
                .type(RefundType.NO_DEDUCTION).build();
    }

    /**
     * 某时刻距「入住日 24:00」的小时数（下限 25），基准时区固定北京。
     * 用服务器时区会随部署环境漂移——生产容器实际跑在 UTC，偏 8 小时会把"还能免费取消多久"说长。
     */
    private static Integer hoursBeforeCheckInEnd(String isoInstant, String checkIn) {
        Instant at = parseFromDate(isoInstant);
        if (at == null) {
            return null;
        }
        try {
            Instant checkInEnd = LocalDate.parse(checkIn).plusDays(1).atStartOfDay(DIDA_ZONE).toInstant();
            return Math.max(MIN_BEFORE, (int) Math.ceil(Duration.between(at, checkInEnd).toMinutes() / 60.0));
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseFromDate(String isoInstant) {
        try {
            return OffsetDateTime.parse(isoInstant).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean positive(Integer count) {
        return count != null && count > 0;
    }

    /** 排障用的逐晚餐食摘要，如 {@code [t=7,a=2]} */
    private static String mealDigest(List<DidaPriceItem> nights) {
        StringBuilder sb = new StringBuilder();
        for (DidaPriceItem night : nights) {
            sb.append("[t=").append(night.getMealType()).append(",a=").append(night.getMealAmount()).append("]");
        }
        return sb.toString();
    }
}
