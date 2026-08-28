package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.ProductKeyFactory;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Expedia 产品规范化与键派生的<b>唯一权威</b>（docs/product-identity.md R-1.1）。
 *
 * <p>三条链路都必须经由本类：查价响应组装、验价 resolve 匹配、目录建档
 * （ExpediaProductMappingService）。键分叉即身份分叉——此前这三个方法长在
 * ExpediaPriceServiceImpl 里，建档服务被迫注入整个查价实现类来借三个方法；
 * 独立成件后依赖方向回正（pricing/content → shared），也给 cursor 供应商迁入
 * 立了适配层两钩子之一（餐食/退改规范化 + 键派生）的实物模板。
 *
 * <p>餐食/退改的规范化输出与展示给客人的口径同源——resolve 换票时"不劣于"比对的
 * 就是这份口径，键与门必须同源。账号成分用 partner_point_of_sale：四个合同参数经
 * {@link ExpediaContractProfile} 启动校验恒为一套已知档案，PPOS 单值即可唯一命名
 * 该档案（R-1.3）。
 */
@Slf4j
@Component
public class ExpediaProductKeyDeriver {

    /**
     * 餐食类 amenity id → 餐食形态。<b>显式映射，不再是"字符串清单 + switch"</b>：
     * 后者曾把 {@code 2102,2103} 漏写成一个 token {@code 21022103}，使两个 case 变成
     * 死代码；又把 {@code 2203} 列进清单却没有对应 case，同样落"无餐食"（issue #97）。
     * 映射形态下"列进来但没归类"在编译期就不可能存在。
     *
     * <p>id 语义取自生产已存的 Content 原始响应（{@code expedia_property_content.raw_json}，
     * 2026-08-19 抽样 300 家 / 2,431 个带 amenities 的 rate 容器实测得到的 name）：
     * {@code 1073742786}=免费早餐(838 次)、{@code 2194}=双早(143)、{@code 2205}=自助早餐(120)、
     * {@code 2102}=全餐(56)、{@code 1073742857}=单早(32)、{@code 2104}=全套早餐(17)、
     * {@code 2207}=含三餐(16)、{@code 2103}=欧式早餐(12)、{@code 2206}=Half board(4)。
     *
     * <p><b>{@code 2203} 有意不收录</b>：该抽样中出现 0 次，语义无从确证。收录并猜成含早
     * 会把无餐食房报成含早（卖错），不收录则最多少报餐食（少卖）——按 R-1.6「宁可少卖
     * 不可卖错」取后者。日后确证其语义再补进本映射即可。
     */
    private static final Map<String, MealKind> MEAL_AMENITIES = Map.ofEntries(
            Map.entry("1073742857", MealKind.BREAKFAST_ONE),      // 单早
            Map.entry("2193", MealKind.BREAKFAST_TWO),            // 双早
            Map.entry("2194", MealKind.BREAKFAST_TWO),            // 双早
            Map.entry("2103", MealKind.BREAKFAST_PER_GUEST),      // 欧式早餐
            Map.entry("2104", MealKind.BREAKFAST_PER_GUEST),      // 全套早餐
            Map.entry("2105", MealKind.BREAKFAST_PER_GUEST),
            Map.entry("2205", MealKind.BREAKFAST_PER_GUEST),      // 自助早餐
            Map.entry("1073742786", MealKind.BREAKFAST_PER_GUEST),// 免费早餐
            Map.entry("1073744734", MealKind.BREAKFAST_PER_GUEST),
            Map.entry("1073744735", MealKind.BREAKFAST_PER_GUEST),
            Map.entry("1073744459", MealKind.BREAKFAST_PER_GUEST),// 咖啡面包形式的早餐
            Map.entry("2106", MealKind.LUNCH_PER_GUEST),          // 免费午餐
            Map.entry("2107", MealKind.DINNER_PER_GUEST),         // 免费晚餐
            Map.entry("2206", MealKind.HALF_BOARD),               // 半包：早 + 晚
            Map.entry("2102", MealKind.FULL_BOARD),               // 全餐：早 + 中 + 晚
            Map.entry("2207", MealKind.FULL_BOARD));              // 含三餐

    /**
     * 餐食形态。份数是入住人数的函数，故不能在映射里写死数值。
     *
     * <p>一条 rate 可能同时下发多个餐食 amenity（实测 {@code 免费早餐 + 全餐} 共现 56 次、
     * {@code 单早 + 自助早餐} 28 次），必须有确定的合并规则，见 {@link #convertMeal}。
     */
    private enum MealKind {
        /** 单早：固定 1 份 */
        BREAKFAST_ONE,
        /** 双早：最多 2 份，入住 1 人时只有 1 份 */
        BREAKFAST_TWO,
        /** 每位客人 1 份早餐 */
        BREAKFAST_PER_GUEST,
        /** 每位客人 1 份午餐 */
        LUNCH_PER_GUEST,
        /** 每位客人 1 份晚餐 */
        DINNER_PER_GUEST,
        /** 半包：早 + 晚 */
        HALF_BOARD,
        /** 全餐 / 全包：早 + 中 + 晚 */
        FULL_BOARD;

        int breakfast(int adultNum) {
            switch (this) {
                case BREAKFAST_ONE: return 1;
                case BREAKFAST_TWO: return Math.min(2, adultNum);
                case BREAKFAST_PER_GUEST:
                case HALF_BOARD:
                case FULL_BOARD: return adultNum;
                default: return 0;
            }
        }

        int lunch(int adultNum) {
            return this == LUNCH_PER_GUEST || this == FULL_BOARD ? adultNum : 0;
        }

        int dinner(int adultNum) {
            return this == DINNER_PER_GUEST || this == HALF_BOARD || this == FULL_BOARD ? adultNum : 0;
        }

        /** 覆盖几种餐——合并多条时用它挑最能代表本 rate 的那条描述 */
        int coverage(int adultNum) {
            return (breakfast(adultNum) > 0 ? 1 : 0) + (lunch(adultNum) > 0 ? 1 : 0)
                    + (dinner(adultNum) > 0 ? 1 : 0);
        }
    }

    /** 测试经 ReflectionTestUtils 替换为固定时钟（判定「罚金窗是否已开」要比对当下） */
    private Clock clock = Clock.systemUTC();

    @Resource
    private ExpediaContractProfile contractProfile;

    /** 仅供测试构造场景使用 */
    public void setContractProfile(ExpediaContractProfile contractProfile) {
        this.contractProfile = contractProfile;
    }

    /**
     * 派生身份<b>及其全部成分</b>（R-2.8：成分只算一次，下游照抄）。
     *
     * <p>建档要落的 {@code meal_signature}/{@code cancel_class}/{@code occupancy} 都从这里取，
     * 不许拿 {@link Meal}/{@link CancelPolicy} 再判一遍——重判必然降维且会与本方法分叉。
     */
    public ProductIdentity deriveIdentity(String supplierHotelId, String supplierRoomId, Meal meal,
                                          List<CancelPolicy> cancelPolicy, String occupancy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.getCount()), isPositive(meal.getLunchCount()), isPositive(meal.getDinnerCount()));
        return ProductIdentity.of(SupplierSourceEnum.EXPEDIA.getCode(), contractProfile.getPartnerPointOfSale(),
                supplierHotelId, supplierRoomId, mealSignature, classifyCancel(cancelPolicy), occupancy);
    }

    /** 退改三分类。UNKNOWN 进 key 照常可售但不进目录——建档问 {@link #isCatalogEligible}（R-5.4）。 */
    private static CancelClass classifyCancel(List<CancelPolicy> cancelPolicy) {
        if (CollectionUtils.isEmpty(cancelPolicy)) {
            return CancelClass.UNKNOWN;
        }
        if (cancelPolicy.stream().anyMatch(p -> Integer.valueOf(1).equals(p.getCancelType())
                && RefundType.NO_DEDUCTION == p.getType())) {
            // 存在免费取消窗口（R-5.1 的 FREE 判据），罚金阶梯照常跟在后面。
            // 旧判据只看 cancelType==1——彼时 convertCancelPolicy 总垫免费头段，两者等价；
            // 头段改为有真免费窗才垫后，纯罚金段也是 cancelType==1，必须再看 NO_DEDUCTION
            return CancelClass.FREE_CANCELLABLE;
        }
        if (cancelPolicy.stream().allMatch(p -> Integer.valueOf(0).equals(p.getCancelType()))) {
            return CancelClass.NON_REFUNDABLE;
        }
        if (cancelPolicy.stream().allMatch(p -> RefundType.DEDUCT_BY_PERCENT == p.getType()
                && p.getValue() != null && p.getValue() >= 100D)) {
            // 每段确定罚≥全款=经济上不可退（同艺龙 CutType=4）。该类随住期可变：
            // 同一卖法临近入住免费窗过期后如实转不可退，与 FREE 的窗口性同理
            return CancelClass.NON_REFUNDABLE;
        }
        // 罚金阶梯存在但判不出全款（按晚/定额/比例<100）：三分类无处安放，按 R-1.6 归 UNKNOWN
        return CancelClass.UNKNOWN;
    }

    /**
     * 餐食与退改是否全部解析成功——即是否可进目录（R-5.4）。判据与 {@link #deriveIdentity}
     * 同源：UNKNOWN 正常参与 key 派生、实时链路照常可售，但进了目录会与「已知不可退」
     * 混为一谈，污染等价类匹配。建档侧不得自行重写判据，只能问这里（R-2.8）。
     */
    public boolean isCatalogEligible(Meal meal, List<CancelPolicy> cancelPolicy) {
        MealSignature mealSignature = meal == null ? MealSignature.unknown()
                : MealSignature.known(isPositive(meal.getCount()), isPositive(meal.getLunchCount()), isPositive(meal.getDinnerCount()));
        return mealSignature.isKnown() && classifyCancel(cancelPolicy) != CancelClass.UNKNOWN;
    }

    /** 只要 key 不要成分时用（如 resolve 匹配只做键比对） */
    public String deriveProductKey(String supplierHotelId, String supplierRoomId, Meal meal,
                                   List<CancelPolicy> cancelPolicy, String occupancy) {
        return deriveIdentity(supplierHotelId, supplierRoomId, meal, cancelPolicy, occupancy).productKey();
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }

    /**
     * 餐食规范化。
     *
     * <p>一条 rate 可能同时下发多个餐食 amenity（生产实测：{@code 免费早餐 + 全餐} 共现
     * 56 次、{@code 单早 + 自助早餐} 28 次）。旧实现按"清单里位置靠后者胜"取单条，而清单
     * 顺序没有任何语义——{@code 免费早餐} 排在 {@code 全餐} 之后，于是全餐 rate 被判成
     * 仅含早，午晚餐丢失、键与"仅含早"合流（issue #97）。合并规则改为两条：
     *
     * <ul>
     *   <li><b>有没有某种餐：取并集</b>。任一 amenity 含早即含早——这才是 productKey 的
     *       {@link MealSignature} 该有的口径，等价类不能因为共现顺序而漂移；</li>
     *   <li><b>份数：取所有含该餐的 amenity 中最少的那个</b>。{@code 单早(1) + 自助早餐(人数)}
     *       共现时报 1 份而非人数——按 R-1.6「宁可少卖不可卖错」取保守侧。</li>
     * </ul>
     *
     * <p>描述取"覆盖餐种最多"的那条（同覆盖时取份数最少者，再同则按 id 字典序），使对外
     * 展示的文案与我方上报的份数同源、且结果确定。
     */
    public Meal convertMeal(Integer adultNum, Map<String, QueryPriceResponse.Amenity> amenities) {
        // 部分 rate 不下发 amenities（实测 2342 行刷价中 30 次），视为无餐食。
        // count 取 0 而非 null：缓存复用时 PriceCacheServiceImpl 会比较 meal.count，null 会空指针
        if (null == amenities) {
            return noMeal();
        }
        int guests = null == adultNum || adultNum < 1 ? 1 : adultNum;

        List<String> matched = new ArrayList<>();
        for (String id : amenities.keySet()) {
            if (MEAL_AMENITIES.containsKey(id)) {
                matched.add(id);
            }
        }
        if (matched.isEmpty()) {
            return noMeal();
        }

        int breakfast = 0;
        int lunch = 0;
        int dinner = 0;
        for (String id : matched) {
            MealKind kind = MEAL_AMENITIES.get(id);
            breakfast = mergeCount(breakfast, kind.breakfast(guests));
            lunch = mergeCount(lunch, kind.lunch(guests));
            dinner = mergeCount(dinner, kind.dinner(guests));
        }

        String descId = matched.stream()
                .min(Comparator
                        .comparingInt((String id) -> -MEAL_AMENITIES.get(id).coverage(guests))
                        .thenComparingInt(id -> MEAL_AMENITIES.get(id).breakfast(guests)
                                + MEAL_AMENITIES.get(id).lunch(guests)
                                + MEAL_AMENITIES.get(id).dinner(guests))
                        .thenComparing(id -> id))
                .orElseThrow();
        QueryPriceResponse.Amenity descAmenity = amenities.get(descId);
        String desc = descAmenity == null || descAmenity.getName() == null ? "" : descAmenity.getName();

        return Meal.builder().count(breakfast).lunchCount(lunch).dinnerCount(dinner).mealDesc(desc).build();
    }

    /** 份数合并：0 表示"该餐种尚未命中"，命中过则取较小者（保守侧，R-1.6） */
    private static int mergeCount(int current, int candidate) {
        if (candidate <= 0) {
            return current;
        }
        return current <= 0 ? candidate : Math.min(current, candidate);
    }

    private static Meal noMeal() {
        return Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
    }

    /** 仅供测试：断言映射里每个 id 都已归类，防止再次出现"列进清单却没有 case" */
    static Map<String, MealKind> mealAmenitiesForTest() {
        return MEAL_AMENITIES;
    }

    /**
     * 查价响应 {@code cancel_penalties} → 契约条款。段语义是时间窗：在 {@code [start,end)}
     * 内取消收该段罚金。
     *
     * <p>转换纪律（2026-08-28 test.ean.com 实测 2,154 条含罚金 rate 采样，见
     * docs/expedia/cancel-penalties.md；生产凭据只在 test.ean.com 有效，线上消费的就是这份形态）：
     * <ul>
     *   <li><b>免费头段只在最早罚金窗 start 晚于当下时垫</b>。采样 40% 的含罚金 rate
     *       （T+1 住期 70%）start 已过且全部 refundable=false——对它们垫头段就是把不能
     *       免费退说成能退，旅客据此取消实收罚金；</li>
     *   <li><b>逐段全部转出</b>，percent 100% 转 DEDUCT_BY_PERCENT value=100（=全款，
     *       同艺龙 CutType=4），不得丢段——旧实现只取 start 最早一段且把 100% 段整个丢弃；</li>
     *   <li><b>载体不认识或时间解析失败 → 空列表=UNKNOWN</b>（R-5.4），不得兜成不可退——
     *       不确定不许说成确定（R-1.6）。</li>
     * </ul>
     *
     * <p>{@code before} = 段截止时刻距入住日 24:00 的小时数（下限 25），入住日按段自带的
     * UTC 偏移解释而非服务器时区——与艺龙侧同一条纪律（服务器时区随部署漂移）。
     * {@code cancel_penalties} 缺席维持旧口径记不可退（采样中未出现，另行实证前不动）。
     */
    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<QueryPriceResponse.CancelPolicy> cancelPolicies) {
        if (CollectionUtils.isEmpty(cancelPolicies)) {
            List<CancelPolicy> nonRefundable = new ArrayList<>();
            nonRefundable.add(CancelPolicy.builder().cancelType(0).build());
            return nonRefundable;
        }
        List<PenaltyWindow> windows = new ArrayList<>();
        for (QueryPriceResponse.CancelPolicy segment : cancelPolicies) {
            OffsetDateTime start = parseOffset(segment.getStart());
            OffsetDateTime end = parseOffset(segment.getEnd());
            if (start == null || end == null) {
                log.info("expedia退改规范化：段时间无法解析，整体按 UNKNOWN 处理(R-5.4),start={},end={}",
                        segment.getStart(), segment.getEnd());
                return List.of();
            }
            windows.add(new PenaltyWindow(start, end, segment));
        }
        windows.sort(Comparator.comparing(w -> w.start().toInstant()));

        List<CancelPolicy> policies = new ArrayList<>();
        OffsetDateTime firstStart = windows.get(0).start();
        if (firstStart.toInstant().isAfter(clock.instant())) {
            Integer before = hoursUntilCheckInEnd(firstStart, checkIn);
            if (before == null) {
                log.info("expedia退改规范化：入住日无法解析，整体按 UNKNOWN 处理(R-5.4),checkIn={}", checkIn);
                return List.of();
            }
            policies.add(CancelPolicy.builder().cancelType(1).timeZone(timeZoneOf(firstStart.getOffset()))
                    .before(before).type(RefundType.NO_DEDUCTION).build());
        }
        for (PenaltyWindow window : windows) {
            CancelPolicy converted = convertPenaltyWindow(checkIn, window);
            if (converted == null) {
                return List.of();
            }
            policies.add(converted);
        }
        return policies;
    }

    /** 单段罚金窗 → 契约段；载体不认识、数值或时间解析不出返回 null（调用方整体按 UNKNOWN） */
    private static CancelPolicy convertPenaltyWindow(String checkIn, PenaltyWindow window) {
        QueryPriceResponse.CancelPolicy raw = window.raw();
        RefundType type;
        Double value;
        if (StringUtils.isNotBlank(raw.getAmount())) {
            type = RefundType.DEDUCT_BY_AMOUNT;
            value = parseNumber(raw.getAmount());
        } else if (StringUtils.isNotBlank(raw.getPercent())) {
            type = RefundType.DEDUCT_BY_PERCENT;
            value = parseNumber(raw.getPercent().replace("%", ""));
        } else if (StringUtils.isNotBlank(raw.getNights())) {
            type = RefundType.DEDUCT_DAY_NIGHT;
            value = parseNumber(raw.getNights());
        } else {
            log.info("expedia退改规范化：罚金载体不认识，整体按 UNKNOWN 处理(R-5.4),amount={},percent={},nights={}",
                    raw.getAmount(), raw.getPercent(), raw.getNights());
            return null;
        }
        Integer before = hoursUntilCheckInEnd(window.end(), checkIn);
        if (value == null || before == null) {
            log.info("expedia退改规范化：罚金数值或入住日无法解析，整体按 UNKNOWN 处理(R-5.4),seg={},checkIn={}",
                    JsonUtils.writeObject2Json(raw), checkIn);
            return null;
        }
        if (value <= 0) {
            // 罚 0（如 nights="0"）=该窗内实际免费
            return CancelPolicy.builder().cancelType(1).timeZone(timeZoneOf(window.end().getOffset()))
                    .before(before).type(RefundType.NO_DEDUCTION).build();
        }
        return CancelPolicy.builder().cancelType(1).timeZone(timeZoneOf(window.end().getOffset()))
                .before(before).type(type).value(value).build();
    }

    private record PenaltyWindow(OffsetDateTime start, OffsetDateTime end, QueryPriceResponse.CancelPolicy raw) {
    }

    private static OffsetDateTime parseOffset(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseNumber(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 某时刻距「入住日 24:00」的小时数（下限 25）；入住日按该时刻自带的 UTC 偏移解释 */
    private static Integer hoursUntilCheckInEnd(OffsetDateTime at, String checkIn) {
        try {
            Instant checkInEnd = LocalDate.parse(checkIn).plusDays(1).atStartOfDay().toInstant(at.getOffset());
            return Math.max(25, (int) Math.ceil(Duration.between(at.toInstant(), checkInEnd).toMinutes() / 60.0));
        } catch (Exception e) {
            return null;
        }
    }

    private static String timeZoneOf(ZoneOffset offset) {
        return "GMT" + (offset.getTotalSeconds() == 0 ? "+00:00" : offset.getId());
    }
}
