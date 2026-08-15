package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
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

    private static final String MEAL_AMENITY_IDS = "1073742857,21022103,2104,2105,2205,1073742786,1073744734,1073744735,2106,2107,2193,2194,2203,2206,2207,1073744459";

    @Resource
    private ExpediaContractProfile contractProfile;

    /** 仅供测试构造场景使用 */
    public void setContractProfile(ExpediaContractProfile contractProfile) {
        this.contractProfile = contractProfile;
    }

    public String deriveProductKey(String supplierHotelId, String supplierRoomId, Meal meal,
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
        return ProductKeyFactory.derive(SupplierSourceEnum.EXPEDIA.getCode(), contractProfile.getPartnerPointOfSale(),
                supplierHotelId, supplierRoomId, mealSignature, cancelClass, occupancy);
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }

    public Meal convertMeal(Integer adultNum, Map<String, QueryPriceResponse.Amenity> amenities) {
        // 部分 rate 不下发 amenities（实测 2342 行刷价中 30 次），视为无餐食。
        // 取值必须与下方 default 分支一致：count 为 0 而非 null，否则缓存复用时
        // CachePriceServiceImpl 的 meal.count.equals(...) 比较会空指针。
        if (null == amenities) {
            return Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        String[] meals = MEAL_AMENITY_IDS.split(",");
        String mealId = "";
        for (String meal : meals) {
            if (amenities.containsKey(meal)) {
                mealId = meal;
            }
        }
        Meal meal = new Meal();
        switch (mealId) {
            case "1073742857": //单早
                meal = Meal.builder().count(1).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2102":  //三餐（早+中+晚）
            case "2207":  //全包
                meal = Meal.builder().count(adultNum).lunchCount(adultNum).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2103":
            case "2104":
            case "2105":
            case "2205":
            case "1073742786":
            case "1073744734":
            case "1073744735":  //免费早餐（份数=入住人数）
            case "1073744459":  //咖啡面包形式的早餐
                meal = Meal.builder().count(adultNum).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2106":  //免费午餐
                meal = Meal.builder().count(0).lunchCount(adultNum).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2107":  //免费晚餐
                meal = Meal.builder().count(0).lunchCount(0).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2193":
            case "2194":  //双早（当入住人数=1时，只有一份）
                meal = Meal.builder().count(Math.min(2, adultNum)).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2206":  //半包
                meal = Meal.builder().count(adultNum).lunchCount(0).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            default:
                meal = Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        return meal;
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
