package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespCacheDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 价格缓存往返必须保住 productKey(R-1.1 / gateway-boundary B1)。
 *
 * <p>2026-08-18 生产实测:缓存读出的产品 productKey=null —— ProductRespCacheDTO
 * 缺该字段,刷价写入时被 BeanUtils.copyProperties 静默丢弃。productKey 是对上游的
 * 不透明句柄与 resolve 换票检索键,丢了它,cursor 走缓存比价的对接直接不成立。
 * 本测试按真实写读路径(copyProperties → JSON → copyProperties)钉死这条链。
 */
class ProductKeyCacheRoundTripTest {

    @Test
    void productKeySurvivesCacheRoundTrip() {
        ProductRespDTO source = ProductRespDTO.builder()
                .hotelId("4173").productId("392135581")
                .productKey("0d9930908dfffe25b2a14a0dae0c5817e430942dd4e0230c2c5f9b8fdb1c573d")
                .supplierId(10010).totalPrice(421285)
                .build();

        // 写侧:CachePriceServiceImpl 240/252 行
        ProductRespCacheDTO cache = new ProductRespCacheDTO();
        BeanUtils.copyProperties(source, cache);
        String json = JsonUtils.encodeJson(cache);

        // 读侧:CachePriceServiceImpl 116-118 行
        ProductRespCacheDTO loaded = JsonUtils.decodeJson(json, new TypeReference<>() {});
        ProductRespDTO out = new ProductRespDTO();
        BeanUtils.copyProperties(loaded, out);

        assertEquals(source.getProductKey(), out.getProductKey(),
                "productKey 在缓存往返中丢失——检查 ProductRespCacheDTO 是否仍有该字段");
        assertEquals("392135581", out.getProductId());
    }

    /** 旧缓存条目(无 productKey 字段的 JSON)必须能读:反序列化为 null,不抛错 */
    @Test
    void legacyCacheEntriesWithoutKeyStillDeserialize() {
        String oldJson = "{\"hotelId\":\"4173\",\"productId\":\"392135581\",\"supplierId\":10010,\"totalPrice\":421285}";
        ProductRespCacheDTO loaded = JsonUtils.decodeJson(oldJson, new TypeReference<>() {});
        assertNull(loaded.getProductKey());
        assertEquals("392135581", loaded.getProductId());
    }
}
