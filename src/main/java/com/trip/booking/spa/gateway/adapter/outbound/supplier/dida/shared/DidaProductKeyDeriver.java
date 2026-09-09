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
import java.util.Map;

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

    /**
     * 分销侧 {@code MealType} → 餐食构成。<b>分销码 = 渠道端 {@code MealTypeID} + 1</b>，判据见
     * {@link #convertMeal}。表内容取自官方渠道管理端文档附录「餐型代码说明」（2026-09-09 查阅）。
     *
     * <p>三类不进表，一律 UNKNOWN：<b>9</b>（渠道 8 = PKG(Room&amp;Ticket)，那是房+票的打包，
     * 根本不是餐食描述）；<b>12~15</b>（渠道 11~14 = Suhur/Iftar 斋月餐系，与早/午/晚不是同一套
     * 划分，且 15 = "Suhur or Iftar" 自带"或"，本就不可确定）；以及表外的任何新值。
     */
    private static final Map<Integer, MealShape> MEAL_TABLE = Map.of(
            1, MealShape.NONE,                      // 渠道 0  Room Only
            2, MealShape.BREAKFAST,                 // 渠道 1  Breakfast Included
            3, MealShape.BREAKFAST_DINNER,          // 渠道 2  Half-Board（早+另一餐，业界惯例为晚餐）
            4, MealShape.BREAKFAST_LUNCH_DINNER,    // 渠道 3  Full-Board
            5, MealShape.BREAKFAST_LUNCH_DINNER,    // 渠道 4  All Inclusive
            6, MealShape.DINNER,                    // 渠道 5  Dinner
            7, MealShape.BREAKFAST_DINNER,          // 渠道 6  BreakfastAndDinner
            8, MealShape.BREAKFAST_LUNCH,           // 渠道 7  BreakfastAndLunch
            10, MealShape.LUNCH,                    // 渠道 9  Lunch
            11, MealShape.LUNCH_DINNER);            // 渠道 10 Lunch And Dinner

    /** 餐食形态。与艺龙同名同义（那边从文案分类，这边从码查表），{@code null} 即 UNKNOWN。 */
    private enum MealShape {
        /** 供应商正面声明「无餐食」——可信的确定信息，不是"没填" */
        NONE(false, false, false),
        BREAKFAST(true, false, false),
        LUNCH(false, true, false),
        DINNER(false, false, true),
        BREAKFAST_LUNCH(true, true, false),
        BREAKFAST_DINNER(true, false, true),
        LUNCH_DINNER(false, true, true),
        BREAKFAST_LUNCH_DINNER(true, true, true);

        final boolean breakfast;
        final boolean lunch;
        final boolean dinner;

        MealShape(boolean breakfast, boolean lunch, boolean dinner) {
            this.breakfast = breakfast;
            this.lunch = lunch;
            this.dinner = dinner;
        }
    }

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
     * <p><b>官方没有给 MealType 的取值表</b>（字段说明只写着 "MealType"），故取值靠统计推。
     * 2026-09-08 两轮生产实测，组合记为（BreakfastType, MealType, MealAmount）：
     * <ul>
     *   <li>样本 A（36 家，日本居多）606 条：(1,1,0) 344、(2,2,2) 210、(2,3,2) 32、(2,7,2) 20</li>
     *   <li>样本 B（可卖清单等距抽 260 家、86 家有货）1844 条：(2,2,2) 1018、(1,1,0) 819、
     *       (1,3,1) 6、(2,3,2) 1 ——<b>1 与 2 两种取值覆盖 99.6%</b>，3 与 7 是长尾</li>
     * </ul>
     * 房型名帮不上忙：样本 B 的 1844 条里，名称含餐食词的 0 条。
     *
     * <p><b>MealAmount 是人份，不是餐数</b>（实测：同一家酒店改占用，1 成人→1、2 成人→2、
     * 3 成人→3、2 成人 1 儿童→3，而 MealType 恒为 2）。故 {@code Meal.count} 填它与艺龙同口径
     * （那边的份数来自文案、这边来自请求占用）；占用本就是 productKey 成分，不会因此键分叉。
     *
     * <p><b>BreakfastType 与逐晚 MealType 会打架，一律以后者为准</b>：样本 A 里 MealType=7 的
     * 样例是日式「1泊2食」（一晚含两餐）而 BreakfastType 仍是 2，照它判就会把早+晚说成仅含早；
     * 样本 B 里另有 (1,3,1) 6 条与占用扫描时见到的 (1,2,2)——BreakfastType 说无早、逐晚却有餐。
     * 官方 price-search 注 8 也写着 BreakfastType 已过时。故只认两种有实证的取值：
     * <ul>
     *   <li>逐晚全部 {@code MealType=1} 且 {@code MealAmount=0} → 确定无餐</li>
     *   <li>逐晚全部 {@code MealType=2} 且 {@code MealAmount>0} → 确定含早（份数取逐晚最大）</li>
     *   <li>其余（含 3、7 及未来新值、逐晚不一致、字段缺失）→ UNKNOWN（R-5.4）</li>
     * </ul>
     * UNKNOWN 照常可售，只是不进产品目录。
     *
     * <p><b>2026-09-09 起按官方餐型表判定</b>（{@link #MEAL_TABLE}），不再只认 1/2。表来自官方
     * <b>渠道管理端</b>文档附录（供应侧推价用，与分销 API 不是同一份），<b>分销码 = 渠道码 + 1</b>。
     * 偏移量由数据定案，不是推断：
     * <ul>
     *   <li><b>同码假设被 2,063 个样本推翻</b>：分销 {@code MealType=1} 的 2,063 条，
     *       {@code MealAmount} <b>无一例外为 0</b>。若同码则 1=Breakfast Included，
     *       含早却零份餐不可能</li>
     *   <li><b>4,279 条报价里从未出现 0</b>，也无表外新值——与「渠道 0~14 加 1 = 分销 1~15」相符</li>
     *   <li>旁证：{@code MealType=7} 的样例是日式「1泊2食」，与渠道 6=BreakfastAndDinner 吻合；
     *       3 与 7 的价格都比同房型含早档贵（+155~+1707），符合「多一餐」</li>
     * </ul>
     * <b>残留风险</b>：那张表印在供应侧文档里，分销侧文档从未引用它。若道旅两侧用的是两套表、
     * 只是前两个值碰巧一致，本判定会错——已列入向客户经理确认的清单（docs §9）。
     *
     * @return {@code null} 表示 UNKNOWN——调用方不得兜成任何确定值
     */
    public Meal convertMeal(DidaRatePlan plan) {
        List<DidaPriceItem> nights = plan.getPriceList();
        if (CollectionUtils.isEmpty(nights)) {
            return null;
        }
        MealShape shape = null;
        int portions = 0;
        for (DidaPriceItem night : nights) {
            MealShape nightShape = MEAL_TABLE.get(night.getMealType());
            int amount = night.getMealAmount() == null ? 0 : night.getMealAmount();
            // 表外取值、逐晚不一致、以及份数与餐食自相矛盾（有餐 0 份 / 无餐却报份数），一律 UNKNOWN
            boolean contradictory = nightShape == MealShape.NONE ? amount != 0 : amount <= 0;
            if (nightShape == null || contradictory || (shape != null && shape != nightShape)) {
                log.info("道旅餐食规范化：MealType 无法归类，按 UNKNOWN 处理(R-5.4),ratePlanId={},逐晚={}",
                        plan.getRatePlanId(), mealDigest(nights));
                return null;
            }
            shape = nightShape;
            portions = Math.max(portions, amount);
        }
        return Meal.builder()
                .count(shape.breakfast ? portions : 0)
                .lunchCount(shape.lunch ? portions : 0)
                .dinnerCount(shape.dinner ? portions : 0)
                .mealDesc("")
                .build();
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
