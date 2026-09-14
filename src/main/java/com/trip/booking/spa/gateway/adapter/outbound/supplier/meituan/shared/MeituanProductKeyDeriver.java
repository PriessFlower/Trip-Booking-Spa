package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanCpApply;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanMealType;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 美团产品规范化与键派生的<b>唯一权威</b>（docs/product-identity.md R-1.1）：餐食／退改规范化
 * + 键派生。查价组装与验价 resolve 匹配都必须经由本类——键分叉即身份分叉。
 *
 * <p>键成分：supplier=MEITUAN、账号=partnerId、supplierRoomId=<b>realRoomId</b>（物理房型）、
 * 餐食、退改类、占用。{@code goodsId} <b>不进键</b>，两条理由各自成立：一是键要表达的是
 * "卖法"，同一卖法在美团可能对应多个 goodsId（实测同一 realRoomId 下同名同床型的产品不止一条），
 * 压进键会让本该合并的卖法分裂；二是它按易腐申报（R-4.2），只进 OfferStore、禁止落库（R-2.1）。
 */
@Slf4j
@Component
public class MeituanProductKeyDeriver {

    /** 退改截止时间的时区：官方把 {@code endDate} 标为北京时间 */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter END_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    @Resource
    private MeituanProperties properties;

    /** 仅供测试构造场景使用 */
    public void setProperties(MeituanProperties properties) {
        this.properties = properties;
    }

    /**
     * 派生身份<b>及其全部成分</b>（R-2.8：成分只算一次，下游照抄）。
     * 建档要落的 meal_signature/cancel_class/occupancy 都从这里取，不许再判一遍。
     */
    public ProductIdentity deriveIdentity(String supplierHotelId, String realRoomId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(positive(meal.getCount()), positive(meal.getLunchCount()),
                        positive(meal.getDinnerCount()));
        CancelClass cancelClass = CancelClassifier.classify(cancelPolicy, null, totalCents, null);
        return ProductIdentity.of(SupplierSourceEnum.MEITUAN.getCode(), properties.getPartnerId(),
                supplierHotelId, realRoomId, mealSignature, cancelClass, occupancy);
    }

    /** 只要 key 不要成分时用（resolve 匹配只做键比对） */
    public String deriveProductKey(String supplierHotelId, String realRoomId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy, Integer totalCents) {
        return deriveIdentity(supplierHotelId, realRoomId, meal, cancelPolicy, occupancy, totalCents).productKey();
    }

    /**
     * 餐食规范化。判据取 {@code ohMealTypeEnum}，理由见 {@link MeituanMealType} 类注释
     * （官方两处文档对 {@code count=-1} 的说法相反，而枚举在 2,926 条实测样本上干净二分）。
     *
     * <p>本家只有早餐一个维度，午餐/晚餐一律填 0——<b>这是"没有这个维度"，不是"确定不含午晚餐"</b>。
     *
     * @return {@code null} 表示 UNKNOWN（R-5.4），调用方不得兜成任何确定值
     */
    public Meal convertMeal(MeituanMealType mealType) {
        String type = mealType == null ? null : StringUtils.trimToNull(mealType.getOhMealTypeEnum());
        if ("NO_BREAKFAST".equals(type)) {
            return meal(0);
        }
        if ("BREAKFAST".equals(type)) {
            Integer count = mealType.getCount();
            if (count == null || count <= 0) {
                // 枚举说含早、份数却说不含或没给：两个字段自相矛盾，不猜哪个对
                log.info("美团餐食规范化：枚举为含早但份数={}，按 UNKNOWN 处理(R-5.4)", count);
                return null;
            }
            return meal(count);
        }
        log.info("美团餐食规范化：餐食枚举缺失或未登记，按 UNKNOWN 处理(R-5.4),ohMealTypeEnum={}", type);
        return null;
    }

    private static Meal meal(int breakfast) {
        return Meal.builder().count(breakfast).lunchCount(0).dinnerCount(0).mealDesc("").build();
    }

    /**
     * 退改规范化：{@code cpApply} → 契约条款。
     *
     * <p>语义与另几家<b>正好相反</b>，是本家最容易读错的一处：一条 {@code cpApply} 不是
     * "从某时起收多少"，而是「<b>在 {@code endDate} 之前</b>取消收 {@code penalty}」——
     * {@code endDate} 是该段的<b>右边界</b>。末段之后不可取消（延续到入住）。
     *
     * <p>{@code penalty} 是<b>定额</b>（分）不是比例，且<b>可能超过我方总价</b>：2026-09-14
     * 生产实测 2,584 个样本，罚金/逐日价之和 集中在 1.07~1.09——美团按自己的售价算罚金，
     * 我们拿到的是底价。折成比例会得出 >100%，故一律按 {@link RefundType#DEDUCT_BY_AMOUNT}
     * 定额透出，由 {@link CancelPolicy#deductsFullPrice} 去判"是否等同不可退"。
     *
     * <p><b>间数口径必须由调用方申明</b>：报价档（batch.goods.rp 无间数字段）给的是<b>单间</b>
     * 罚金，下单前校验（order.check 带 roomNum）给的是<b>全部间数</b>罚金——实测同一产品两晚，
     * 1 间回 15098/25164，2 间回 30146/50244。故本方法要求传入 {@code rooms}，把单间口径
     * 乘上去；传错就是按一间的罚金去承诺多间的单子。
     *
     * @param penaltyPerRoom true=入参是单间口径（报价档），false=已是全部间数口径（验价档）
     * @return 解析不出返回空列表（R-5.4），调用方不得据此声称"可免费取消"
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, Integer refundable,
                                                  List<MeituanCpApply> raw, int rooms,
                                                  boolean penaltyPerRoom, Instant now) {
        if (Integer.valueOf(1).equals(refundable)) {
            // 明确不可退。实测 400 个"refundable=1 却带 cpApply"的段里，罚金为 0 的有 0 个
            // ——即这个标记与它的阶梯并不打架，照它判不会埋掉任何免费窗
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        if (CollectionUtils.isEmpty(raw)) {
            // 可退但没给阶梯：判不出何时收多少，UNKNOWN（照常可售，只是不进目录）
            return List.of();
        }
        List<MeituanCpApply> sorted = new ArrayList<>();
        for (MeituanCpApply item : raw) {
            if (item == null || item.getPenalty() == null || endOf(item) == null) {
                log.info("美团退改规范化：分段缺 penalty 或 endDate 不可解析，整体按 UNKNOWN 处理(R-5.4),endDate={}",
                        item == null ? null : item.getEndDate());
                return List.of();
            }
            sorted.add(item);
        }
        sorted.sort(Comparator.comparing(MeituanProductKeyDeriver::endOf));

        int multiplier = penaltyPerRoom ? Math.max(1, rooms) : 1;
        List<CancelPolicy> policies = new ArrayList<>();
        for (MeituanCpApply item : sorted) {
            Integer before = CancelClassifier.beforeHours(endOf(item), checkIn, BEIJING);
            if (before == null) {
                return List.of();
            }
            long penalty = item.getPenalty() * multiplier;
            policies.add(penalty <= 0
                    ? CancelPolicy.builder().cancelType(1).timeZone(BEIJING.getId()).before(before)
                            .type(RefundType.NO_DEDUCTION).build()
                    : CancelPolicy.builder().cancelType(1).timeZone(BEIJING.getId()).before(before)
                            .type(RefundType.DEDUCT_BY_AMOUNT).amount(Math.toIntExact(penalty)).build());
        }
        // 末段之后不可取消：这是本家阶梯的隐含收尾，不写出来就等于说"末段截止后仍按末段罚"
        policies.add(CancelPolicy.builder().cancelType(0).timeZone(BEIJING.getId())
                .before(CancelClassifier.MIN_BEFORE).build());
        // 过期段不许流出：截止时刻已过的段行使不了，既不能对外承诺，也不该参与判类
        return CancelClassifier.liveSegments(policies, checkIn, now);
    }

    /** 段的截止时刻。{@code endDate} 是北京时间，格式 MM/dd/yyyy HH:mm；解析不出返回 null */
    static Instant endOf(MeituanCpApply item) {
        String raw = item == null ? null : StringUtils.trimToNull(item.getEndDate());
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, END_DATE).atZone(BEIJING).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** 份数 > 0 即"有这一餐"。MealSignature 只认有/无，不记份数 */
    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
