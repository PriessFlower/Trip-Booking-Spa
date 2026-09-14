package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyCancellationPenalty;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.CancelClassifier;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.Meal;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 差旅无忧产品规范化与键派生的<b>唯一权威</b>（docs/product-identity.md R-1.1）：餐食／退改规范化
 * + 键派生。查价组装与验价 resolve 匹配都必须经由本类——键分叉即身份分叉。
 *
 * <p>键成分：supplier=CLWY、账号=Wid、supplierRoomId=<b>RoomType.roomTypeId</b>、餐食、退改类、占用。
 * {@code ratePlanId} 是分代轮换的易腐报价码，<b>不进键</b>。
 */
@Slf4j
@Component
public class ClwyProductKeyDeriver {

    @Resource
    private ClwyProperties properties;

    /** 仅供测试构造场景使用 */
    public void setProperties(ClwyProperties properties) {
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
        return ProductIdentity.of(SupplierSourceEnum.CLWY.getCode(), properties.getWid(),
                supplierHotelId, roomTypeId, mealSignature, cancelClass, occupancy);
    }

    /** 只要 key 不要成分时用（resolve 匹配只做键比对） */
    public String deriveProductKey(String supplierHotelId, String roomTypeId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        return deriveIdentity(supplierHotelId, roomTypeId, meal, cancelPolicy, occupancy, totalCents).productKey();
    }

    /**
     * 餐食规范化。本家<b>只有一个餐食字段</b>：{@code Breakfast}「早餐份数」（官方 05-product-price
     * 字段表，2026-09-14 查阅），是 int 不是布尔，没有午餐/晚餐维度。
     *
     * <p>判定因此比另三家简单，且不需要任何统计推断：
     * <ul>
     *   <li>{@code breakfast == 0} → <b>确定无餐</b>（供应商正面声明 0 份，不是"没填"）</li>
     *   <li>{@code breakfast > 0} → 确定含早，份数即该值</li>
     *   <li>字段缺席（null）→ UNKNOWN（R-5.4）。照常可售，只是不进目录</li>
     *   <li>负数 → UNKNOWN。没有"负份早餐"这种东西，出现即报文异常，不猜</li>
     * </ul>
     *
     * <p>午餐与晚餐一律填 0：<b>这是"本家没有这个维度"，不是"确定没有午晚餐"</b>。对 MealSignature
     * 而言两者同形（都判成 false），此处如实记下差别，免得日后有人拿它当"clwy 确认不含晚餐"的依据。
     *
     * @return {@code null} 表示 UNKNOWN——调用方不得兜成任何确定值
     */
    public Meal convertMeal(ClwyRatePlan plan) {
        Integer breakfast = plan == null ? null : plan.getBreakfast();
        if (breakfast == null || breakfast < 0) {
            log.info("clwy 餐食规范化：早餐份数缺失或为负，按 UNKNOWN 处理(R-5.4),ratePlanId={},breakfast={}",
                    plan == null ? null : plan.getRatePlanId(), breakfast);
            return null;
        }
        return Meal.builder()
                .count(breakfast)
                .lunchCount(0)
                .dinnerCount(0)
                .mealDesc("")
                .build();
    }

    /**
     * 退改规范化：{@code CancellationPenalties} → 契约条款。
     *
     * <p>语义与道旅同形（官方 05-product-price 字段说明）：一条政策 = 「<b>从 {@code from} 起</b>
     * 取消收 {@code amount}」，之前免费。故把「起始点列表」翻成本仓的「分段」：段 i 的截止时刻 =
     * 下一条的 {@code from}，末段无截止（落 {@code before} 下限 25，表示"此后一直"）；
     * 首条 {@code amount>0} 时补一段免费窗，其截止时刻即首条 {@code from}。
     *
     * <p><b>时刻的表达与文档不符，按实测口径解析</b>（2026-09-14 生产实测 8 家 883 条报价，
     * 详见 {@code ClwyCancellationPenalty} 类注释）：{@code from} 是<b>不带偏移的本地时刻</b>
     * （{@code "2026-09-13 23:00:00"}），{@code cityTimeZone} 是<b>小时偏移字符串</b>
     * （{@code "1"}/{@code "-8"}/{@code "12"}）而非时区名。两者相加才是一个确定时刻。
     *
     * <p>取用顺序：
     * <ol>
     *   <li>{@code from} + {@code cityTimeZone}——两个都是官方文档里有的字段，语义明确，首选</li>
     *   <li>{@code newFrom}——文档没有但线上在发，自带偏移；偏移是单位数（{@code +1:00}）不合 ISO，
     *       用前补零。仅当 cityTimeZone 缺席或不可解析时兜底</li>
     *   <li>都不行 → UNKNOWN（空列表）。<b>不套默认时区</b>：差一小时就把"还能免费取消多久"说错，
     *       而这正是要赔钱的那种错</li>
     * </ol>
     *
     * <p>覆盖率不是 100%：实测 883 条报价里 716 条（81.1%）同时有 cityTimeZone 与
     * cancellationPenalties，其余 167 条两者皆无 → 退改 UNKNOWN，照常可售但不进目录（R-5.4）。
     *
     * <p>空政策列表一律 UNKNOWN。<b>cursor 把它兜成"不可退"</b>（ClwyCancellationConvertor
     * 的 {@code buildNonRefundable}）是防赔款的故意设计，SPA 侧用 UNKNOWN 达成同等防护而不说谎
     * （R-5.4）。
     *
     * @return 解析不出返回空列表（R-5.4），调用方不得据此声称"可免费取消"
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<ClwyCancellationPenalty> raw,
                                                  String cityTimeZone) {
        return convertCancelPolicy(checkIn, raw, cityTimeZone, Instant.now());
    }

    /**
     * 同上，但由调用方给时钟。{@code now} 决定哪些段已过期——过期判定是本方法输出的一部分，
     * 把它留给 {@code Instant.now()} 就没法钉住"免费窗过期后不再对外承诺免费"这条判据
     * （守护 R63：夹具会随真实时间腐烂）。
     */
    List<CancelPolicy> convertCancelPolicy(String checkIn, List<ClwyCancellationPenalty> raw,
                                           String cityTimeZone, Instant now) {
        if (CollectionUtils.isEmpty(raw)) {
            return List.of();
        }
        List<ClwyCancellationPenalty> sorted = new ArrayList<>();
        for (ClwyCancellationPenalty penalty : raw) {
            if (penalty == null || penalty.getAmount() == null
                    || (penalty.getFrom() == null && penalty.getNewFrom() == null)) {
                log.info("clwy 退改规范化：分段缺 from/amount 或时刻不可解析，整体按 UNKNOWN 处理(R-5.4),段={}",
                        penalty == null ? null : penalty.getFrom());
                return List.of();
            }
            sorted.add(penalty);
        }
        ZoneOffset offset = parseHourOffset(cityTimeZone);
        if (offset == null && instantOf(sorted.get(0), null) == null) {
            log.info("clwy 退改规范化：取不到时刻（cityTimeZone={} 不可解析且 newFrom 也不可用），"
                    + "按 UNKNOWN 处理(R-5.4)", cityTimeZone);
            return List.of();
        }
        final ZoneOffset zoneOffset = offset;
        for (ClwyCancellationPenalty penalty : sorted) {
            if (instantOf(penalty, zoneOffset) == null) {
                log.info("clwy 退改规范化：某段时刻不可解析，整体按 UNKNOWN 处理(R-5.4),from={},newFrom={}",
                        penalty.getFrom(), penalty.getNewFrom());
                return List.of();
            }
        }
        sorted.sort(Comparator.comparing(p -> instantOf(p, zoneOffset)));

        List<CancelPolicy> policies = new ArrayList<>();
        String zoneLabel = zoneOffset == null ? "UTC" : zoneOffset.getId();
        ZoneId zone = zoneOffset == null ? ZoneOffset.UTC : zoneOffset;
        BigDecimal firstAmount = sorted.get(0).getAmount();
        if (firstAmount.signum() > 0) {
            Integer before = CancelClassifier.beforeHours(instantOf(sorted.get(0), zoneOffset), checkIn, zone);
            if (before == null) {
                return List.of();
            }
            policies.add(free(before, zoneLabel));
        }
        for (int i = 0; i < sorted.size(); i++) {
            ClwyCancellationPenalty next = i + 1 < sorted.size() ? sorted.get(i + 1) : null;
            Integer before = next == null ? CancelClassifier.MIN_BEFORE
                    : CancelClassifier.beforeHours(instantOf(next, zoneOffset), checkIn, zone);
            if (before == null) {
                return List.of();
            }
            BigDecimal amount = sorted.get(i).getAmount();
            policies.add(amount.signum() <= 0 ? free(before, zoneLabel)
                    : CancelPolicy.builder().cancelType(1).timeZone(zoneLabel).before(before)
                            .type(RefundType.DEDUCT_BY_AMOUNT).value(amount.doubleValue()).build());
        }
        // 过期段不许流出：截止时刻已过的段行使不了，既不能对外承诺，也不该参与判类
        return CancelClassifier.liveSegments(policies, checkIn, now);
    }

    /**
     * 解析 {@code cityTimeZone}——它是<b>小时偏移</b>不是时区名（实测 {@code "1"}/{@code "-8"}/{@code "12"}）。
     * 支持带正负号与小数（印度 +5.5、尼泊尔 +5.75 这类半时区尚未实测到，但格式上留出）。
     * 解析不出返回 null。
     */
    static ZoneOffset parseHourOffset(String cityTimeZone) {
        String raw = StringUtils.trimToNull(cityTimeZone);
        if (raw == null) {
            return null;
        }
        try {
            double hours = Double.parseDouble(raw);
            int totalMinutes = (int) Math.round(hours * 60);
            if (Math.abs(totalMinutes) > 18 * 60) {
                return null;
            }
            return ZoneOffset.ofTotalSeconds(totalMinutes * 60);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 一条政策的绝对时刻：优先 {@code from}+偏移，兜底 {@code newFrom}（补零后按 ISO 解）。
     * 都不行返回 null。
     */
    static Instant instantOf(ClwyCancellationPenalty penalty, ZoneOffset offset) {
        if (penalty == null) {
            return null;
        }
        String from = StringUtils.trimToNull(penalty.getFrom());
        if (from != null && offset != null) {
            try {
                return LocalDateTime.parse(from.replace(' ', 'T')).toInstant(offset);
            } catch (Exception ignored) {
                // 落到 newFrom 兜底
            }
        }
        return parseNewFrom(penalty.getNewFrom());
    }

    /**
     * 解析 {@code newFrom}。它的偏移是<b>单位数</b>（{@code "2026-09-13T23:00:00+1:00"}），
     * ISO 要求两位，故先补零再解。文档没有这个字段，仅作兜底。
     */
    static Instant parseNewFrom(String newFrom) {
        String raw = StringUtils.trimToNull(newFrom);
        if (raw == null) {
            return null;
        }
        String normalized = raw.replaceAll("([+-])(\\d):", "$10$2:");
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static CancelPolicy free(int before, String zoneLabel) {
        return CancelPolicy.builder().cancelType(1).timeZone(zoneLabel).before(before)
                .type(RefundType.NO_DEDUCTION).build();
    }

    private static boolean positive(Integer count) {
        return count != null && count > 0;
    }
}
