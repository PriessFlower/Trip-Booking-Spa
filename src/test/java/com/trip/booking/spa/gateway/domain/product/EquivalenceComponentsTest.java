package com.trip.booking.spa.gateway.domain.product;

import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等价类成分透出（R-2.10）：上游要按「同一种卖法」跨供应商归并，而餐食/退改/占用这三样
 * 只有网关判得了（各家报文写法不一，归一化在适配层）。让上游重判必然分叉，那正是 R-2.8
 * 消灭过一次的东西。
 *
 * <p>本类守两件事：**该出去的出去了**（且两条路径都出），**不该出去的没跟着出去**。
 */
class EquivalenceComponentsTest {

    private static Product quoted() {
        ProductIdentity identity = ProductIdentity.of(10025, "acct-1", "H1", "R1",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, "2");
        Product product = Product.builder()
                .hotelId("H1").productId("rate-token-会过期")
                .productKey(identity.productKey()).identity(identity)
                .supplierId(10025).build();
        product.stampEquivalence();
        return product;
    }

    @Test
    @DisplayName("出参 JSON 里带三个成分")
    void componentsAreOnTheWire() {
        String json = JsonUtils.writeObject2Json(quoted());

        assertTrue(json.contains("\"mealSignature\":\"B1L0D0\""), "餐食签名没出去：" + json);
        assertTrue(json.contains("\"cancelClass\":\"FREE_CANCELLABLE\""), "退改档没出去：" + json);
        assertTrue(json.contains("\"occupancy\":\"2\""), "占用没出去：" + json);
    }

    /**
     * 账号与供应商酒店/房型 id 凑齐就能反推 productKey——等于把身份发号权交出去（R-1.5 禁止发号）。
     * 所以透的是三个成分，不是整个 {@code identity}。
     */
    @Test
    @DisplayName("内部执行材料不许跟着出去")
    void internalsStayInside() {
        String json = JsonUtils.writeObject2Json(quoted());

        assertFalse(json.contains("acct-1"), "账号泄漏到出参了：" + json);
        assertFalse(json.contains("\"identity\""), "identity 整个被序列化了：" + json);
        assertFalse(json.contains("supplierRoomId"), "供应商房型 id 泄漏了：" + json);
    }

    /**
     * 走缓存那条路的成分<b>不从 Redis 来</b>：它们是稳定信息，进 Redis 就成了同一事实的
     * 第二份拷贝，判据一改两份必然对不上（R-2.6 / {@code QuotePayloadContentTest} 的白名单）。
     * 来源是档案表——缓存里的 productKey 就是通往它的桥。
     *
     * <p>这条盯的是那座桥有没有接上：漏一处不报错，只是走缓存的报价三个字段恒空，
     * 而生产上绝大多数报价正是走缓存出去的。
     */
    @Test
    @DisplayName("走缓存的成分从档案表补，不塞进 Redis")
    void cachedQuotesGetComponentsFromTheCatalog() throws Exception {
        String cacheRead = Files.readString(Path.of("src/main/java/com/trip/booking/spa/gateway"
                + "/adapter/outbound/state/pricecache/PriceCacheServiceImpl.java"));
        String catalogSql = Files.readString(Path.of("src/main/resources/mapper/ProductCatalogMapper.xml"));

        assertTrue(cacheRead.contains("setMealSignature(attr.getMealSignature())")
                        && cacheRead.contains("setCancelClass(attr.getCancelClass())")
                        && cacheRead.contains("setOccupancy(attr.getOccupancy())"),
                "读缓存时没把成分从档案表拓到出参上——走缓存的报价三个字段会恒空");

        assertTrue(catalogSql.contains("occupancy"),
                "档案表查询没选出 occupancy——查不到不报错，只是这个成分恒空");
    }

    /**
     * 实时查价那条路不经档案表（现查的产品可能还没建档），成分直接从 identity 拓。
     * 两条路各有各的来源，缺一条就是「某一类报价没有成分」，而那不会报错。
     */
    @Test
    @DisplayName("实时查价从 identity 拓成分")
    void liveQuotesStampFromIdentity() throws Exception {
        String template = Files.readString(Path.of("src/main/java/com/trip/booking/spa/gateway"
                + "/application/pricing/AbstractProductSyncSupportService.java"));

        assertTrue(template.contains("stampEquivalence"),
                "实时查价模板没拓成分——上游拿实时报价时三个字段恒空");
    }
}
