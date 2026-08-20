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
import com.trip.booking.spa.platform.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
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
        CancelClass cancelClass;
        if (CollectionUtils.isEmpty(cancelPolicy)) {
            cancelClass = CancelClass.UNKNOWN;
        } else if (cancelPolicy.stream().anyMatch(p -> Integer.valueOf(1).equals(p.getCancelType()))) {
            cancelClass = CancelClass.FREE_CANCELLABLE;
        } else {
            cancelClass = CancelClass.NON_REFUNDABLE;
        }
        return ProductIdentity.of(SupplierSourceEnum.EXPEDIA.getCode(), contractProfile.getPartnerPointOfSale(),
                supplierHotelId, supplierRoomId, mealSignature, cancelClass, occupancy);
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
        // count 取 0 而非 null：缓存复用时 CachePriceServiceImpl 会比较 meal.count，null 会空指针
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

    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<QueryPriceResponse.CancelPolicy> cancelPolicies) {
        List<CancelPolicy> cancelPolicyList = new ArrayList<>();

        QueryPriceResponse.CancelPolicy cancelPolicy = null;
        if (CollectionUtils.isEmpty(cancelPolicies)) {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
            return cancelPolicyList;
        }
        cancelPolicy = cancelPolicies.stream().min(Comparator.comparing(QueryPriceResponse.CancelPolicy::getStart)).get();
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int beforeEnd = 0;
        int beforeStart = 0;
        try {
            beforeEnd = DateUtil.diffHour(sdfTime.parse(cancelPolicy.getEnd()), sdfDate.parse(checkIn + " 24:00:00"));
            beforeStart = DateUtil.diffHour(sdfTime.parse(cancelPolicy.getStart()), sdfDate.parse(checkIn + " 24:00:00"));
        } catch (Exception e) {
            log.info("时间转换校验异常", e);
        }
        if (StringUtils.isNotBlank(cancelPolicy.getAmount())) {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
            if (beforeStart > 25) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(beforeEnd).type(RefundType.DEDUCT_BY_AMOUNT).value(Double.valueOf(cancelPolicy.getAmount())).build());
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getPercent())) {
            if ("100%".equals(cancelPolicy.getPercent())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.DEDUCT_BY_PERCENT).value(Double.valueOf(cancelPolicy.getPercent().replace("%", ""))).build());
                }
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getNights())) {
            if ("0".equals(cancelPolicy.getNights())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.NO_DEDUCTION).build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.DEDUCT_DAY_NIGHT).value(Double.valueOf(cancelPolicy.getNights())).build());
                }
            }
        } else {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
        }
        return cancelPolicyList;
    }

    private static String subDateGMT(String cancelDate) {
        return "GMT" + cancelDate.substring(cancelDate.length() - 6, cancelDate.length() - 3);
    }
}
