package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespCacheDTO;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫 R-2.6：产品详情缓存<b>只许</b>放易腐码、通往目录的桥、当轮价格。
 *
 * <p>为什么要一条测试盯着字段清单：加字段到 DTO 是一行代码的事，且写读两侧都是
 * {@code BeanUtils.copyProperties} 按名复制——加上去就自动生效，没有任何编译期阻力。
 * 而每加一个稳定字段，Redis 就多一份和库里重复的副本，并重新把缓存变成事实源
 * （2026-08-19 换票基准错取刷价快照单晚价，根因正是这个）。
 *
 * <p>所以本测试<b>故意用白名单而非黑名单</b>：新增字段一律先失败，逼作者回答
 * 「这个信息供应商明天换一批报价码后还成立吗」——成立就该进库表，不该进这里。
 */
class CacheDtoPerishableOnlyTest {

    /** 易腐（随供应商会话轮换） */
    private static final Set<String> PERISHABLE = Set.of("productId", "planSession");
    /** 桥：出价握着易腐 productId，靠它才能回查目录里的稳定属性（productId 依 R-2.1 不落库） */
    private static final Set<String> BRIDGE = Set.of("productKey");
    /** 当轮价格：实时性要求最高，必须在缓存 */
    private static final Set<String> PRICE = Set.of("totalPrice", "totalTaxes", "roomTotalPrice",
            "stayPrice", "storePayPrice", "storePayCurrency", "brokerage", "currencyType", "currency");

    @Test
    @DisplayName("缓存 DTO 不得出现稳定属性字段（白名单，新增即失败）")
    void onlyPerishableBridgeAndPrice() {
        List<String> actual = Arrays.stream(ProductRespCacheDTO.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .sorted()
                .collect(Collectors.toList());

        List<String> allowed = java.util.stream.Stream.of(PERISHABLE, BRIDGE, PRICE)
                .flatMap(Set::stream).sorted().collect(Collectors.toList());

        assertEquals(allowed, actual,
                "缓存 DTO 字段清单变了。新增字段前先判腐性（R-2.6）：稳定的进 supplier_product_base，"
                        + "只有易腐/桥/当轮价格才可进 Redis。确实该加，请同步更新本测试的白名单");
    }

    @Test
    @DisplayName("桥字段 productKey 必须存在——删它等于把库里属性永久锁死")
    void bridgeFieldMustSurvive() {
        assertTrue(Arrays.stream(ProductRespCacheDTO.class.getDeclaredFields())
                        .anyMatch(f -> "productKey".equals(f.getName())),
                "productKey 是缓存与目录之间唯一的桥：出价只握有易腐 productId，"
                        + "而 productId 依 R-2.1 不落库，桥断了属性就再也查不回来");
    }

    @Test
    @DisplayName("瘦身实测：单条序列化体积应显著小于瘦身前的约 1,225 字节")
    void payloadIsSmall() {
        ProductRespCacheDTO dto = new ProductRespCacheDTO();
        dto.setProductId("41000000000_0_0_37281_1_0_0");
        dto.setProductKey("a".repeat(64));
        dto.setPlanSession("cf9a1e2b-7d3c-4a58-9f01-2b6e8c4d7a90");
        dto.setTotalPrice(124510);
        dto.setTotalTaxes(0);
        dto.setRoomTotalPrice(124510);
        dto.setCurrency("CNY");
        dto.setCurrencyType("CNY");

        int bytes = JsonUtils.writeObject2Json(dto).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        // 阈值取 600：留出未来价格字段填满的余量，同时仍远低于瘦身前的 1,225
        assertTrue(bytes < 600, "缓存单条体积 " + bytes + " 字节，超出预期。"
                + "Redis 实测 17.5 万条详情占 1.19G/2G，体积回涨会直接顶到 maxmemory");
    }
}
