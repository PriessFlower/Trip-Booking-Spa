package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 按 productKey 从目录读产品的<b>稳定属性</b>（R-2.6 的读侧）。
 *
 * <p><b>它存在的前提是那条"桥"</b>：Redis 只留易腐码与 productKey，稳定属性在库里。
 * 出价拿到的是易腐的 productId，靠缓存里的 productKey 转一道才能查到属性——
 * 桥断了，进了库的东西就再也找不回来（productId 本身<b>不在</b>库里，R-2.1 禁止落库）。
 *
 * <p><b>为什么敢在出价主路径上查库</b>：productKey → 属性这个映射<b>跨刷价周期稳定</b>
 * （房型、餐食、退改类变了，productKey 本身就变了，等于换了一个键）。故可长期驻留进程内
 * 缓存，命中后零 IO；只有首次与冷键穿透到库。这与"价格必须实时"是两回事——价格在 Redis。
 *
 * <p>缓存<b>不设 TTL 只设容量上限</b>：稳定属性没有过期概念，只有内存约束。
 * 容量满时按插入顺序淘汰最旧的（简单 FIFO 足够——热门酒店会被反复查回，冷键淘汰无害）。
 */
@Slf4j
@Service
@RefreshScope
public class ProductAttributeReader {

    @Resource
    private ProductCatalogMapper productCatalogMapper;

    /** 进程内缓存容量上限。按实测每条约 300 字节估，5 万条约 15MB */
    @Value("${cache.product-attribute.max-size:50000}")
    private int maxSize;

    private final Map<String, ProductAttribute> local = new ConcurrentHashMap<>();

    /**
     * 批量取属性。查不到的 key 不出现在返回中——调用方须容忍缺失
     * （建档尚未覆盖到该产品、或该产品是 UNKNOWN 不进目录，两者都是正常态）。
     */
    public Map<String, ProductAttribute> batchGet(int supplierId, List<String> productKeys) {
        if (productKeys == null || productKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        // 必须先去重：一次出价里多个易腐 productId 会指向同一个 productKey（等价卖法本就是
        // 多对一，实测一家酒店 65 条报价只对应 33 个卖法）。不去重则重复键会白查一遍库，
        // 且覆盖率分母被重复放大——33/66 看着像只覆盖一半，实则 33/33 全覆盖。
        java.util.Set<String> distinctKeys = new java.util.LinkedHashSet<>(productKeys);
        Map<String, ProductAttribute> result = new HashMap<>(distinctKeys.size());
        List<String> missing = new java.util.ArrayList<>();
        for (String key : distinctKeys) {
            ProductAttribute hit = local.get(cacheKey(supplierId, key));
            if (hit != null) {
                result.put(key, hit);
            } else {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            reportCoverage(supplierId, distinctKeys.size(), distinctKeys.size());
            return result;
        }
        try {
            List<Map<String, Object>> rows = productCatalogMapper.selectAttributesByProductKeys(supplierId, missing);
            for (Map<String, Object> row : rows) {
                String key = str(row.get("product_key"));
                if (key == null) {
                    continue;
                }
                ProductAttribute attr = ProductAttribute.builder()
                        .roomId(str(row.get("supplier_room_id")))
                        .productName(str(row.get("supplier_product_name")))
                        .mealSignature(str(row.get("meal_signature")))
                        .cancelClass(str(row.get("cancel_class")))
                        .build();
                result.put(key, attr);
                putLocal(cacheKey(supplierId, key), attr);
            }
        } catch (Exception e) {
            // 读属性失败不该让出价整体失败：退化为"只有价格没有属性"，由调用方按缺失处理
            log.warn("产品属性读取失败,supplierId={},keys={}条,err={}", supplierId, missing.size(), e.toString());
        }
        reportCoverage(supplierId, distinctKeys.size(), result.size());
        return result;
    }

    /**
     * 上报目录覆盖率——<b>这条观测是发布顺序的看门人</b>。
     *
     * <p>缓存瘦身后，属性的唯一来源是目录表。若先发瘦身、后开建档，出价会静默地只带价格
     * 不带房型餐食：SPA 自身一切正常（不报错、不失败），而下游按房型做映射的装配会整片
     * 落空。这种"看起来健康的残缺"没有异常可抓，只能靠覆盖率数字暴露。
     *
     * <p>依 O-1.2 生在指标通道而非日志：它是要跨天对比的业务数字（建档铺开的进度曲线），
     * 不是排障现场。维度进 tag 不进名字（O-2.1）。
     */
    private static void reportCoverage(int supplierId, int asked, int hit) {
        if (asked <= 0) {
            return;
        }
        Map<String, Object> tags = new HashMap<>(2);
        tags.put("supplier", String.valueOf(supplierId));
        Monitor.recordMany("catalog_attribute_asked", tags, asked);
        Monitor.recordMany("catalog_attribute_hit", tags, hit);
    }

    private void putLocal(String key, ProductAttribute attr) {
        if (local.size() >= maxSize) {
            // 容量保护：稳定属性无过期概念，只需防无界增长。冷键被淘汰后下次查回即可
            java.util.Iterator<String> it = local.keySet().iterator();
            for (int i = 0; i < Math.max(1, maxSize / 10) && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
        local.put(key, attr);
    }

    private static String cacheKey(int supplierId, String productKey) {
        return supplierId + ":" + productKey;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOf(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof Number ? ((Number) o).intValue() : Integer.valueOf(String.valueOf(o));
    }

    /** 目录里的稳定属性子集——只放出价响应真正要用的，不做全量映射 */
    @Data
    @Builder
    public static class ProductAttribute {
        private String roomId;
        private String productName;
        /** {@code MealSignature.canonical()}，如 {@code B1L0D0} */
        private String mealSignature;
        /** {@code CancelClass} 名，如 {@code FREE_CANCELLABLE} */
        private String cancelClass;

        public Room toRoom() {
            return Room.builder().roomId(roomId).roomName(productName).build();
        }

        /**
         * 由规范串还原餐食。
         *
         * <p>改造前这里只能 {@code setLunchCount(0)}/{@code setDinnerCount(0)} 硬填 0——
         * 因为旧列 {@code breakfast INT} 只有一位，午晚餐信息在落库时就丢了。
         * 档案表按 R-2.7 改存规范串之后，三顿都能如实还原。
         *
         * <p><b>份数不在档案里</b>：份数是入住人数的函数（占用已单独进键），档案只记"有没有"。
         * 故还原为 1/0，代表"含/不含"。
         */
        public Meal toMeal() {
            Meal meal = new Meal();
            meal.setCount(hasMeal('B') ? 1 : 0);
            meal.setLunchCount(hasMeal('L') ? 1 : 0);
            meal.setDinnerCount(hasMeal('D') ? 1 : 0);
            return meal;
        }

        /** {@code B1L0D0} 里某一位是否为 1；串不合法（含 UNKNOWN）一律按不含——宁可少卖不可卖错 */
        private boolean hasMeal(char slot) {
            if (mealSignature == null || mealSignature.length() != 6) {
                return false;
            }
            int i = mealSignature.indexOf(slot);
            return i >= 0 && i + 1 < mealSignature.length() && mealSignature.charAt(i + 1) == '1';
        }

        public ProductInfo toProductInfo() {
            return ProductInfo.builder().productName(productName).productStatus(1).build();
        }
    }

    /** 供测试与诊断：当前进程内缓存条数 */
    public int cachedSize() {
        return local.size();
    }
}
