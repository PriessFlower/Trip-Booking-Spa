package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespCacheDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下架改 HDEL 的真实数据 e2e（§2.2.2：真库真数据，非 mock）。<b>手动跑</b>，CI 不含：
 *
 * <pre>
 * docker run -d --name spa-e2e-redis-hdel -p 63790:6379 redis:7-alpine
 * mvn test -Dtest=DelistByHdelRealDataE2EManual \
 *   -Dspa.e2e.dump=/path/e2e-dump.json -Dspa.e2e.redis.port=63790
 * </pre>
 *
 * <p>dump 是从生产 Redis 原样导出的一个价格 Hash（含真墓碑）+ 若干真 quote 详情，
 * 格式 {@code {"priceKey":..., "hash":{field:json}, "quotes":{field:json}}}。
 *
 * <p>同数据 A/B：A=墓碑在场时读一遍（即改动前写侧留下的真实形态）；跑一轮新写侧
 * （在售清单=有 quote 的那批）；B=再读一遍。断言 ①A==B（读侧产出逐字段相等）；
 * ②墓碑与本轮缺席者全部被 HDEL；③缓存里不再有任何 {"price":0}。
 */
@EnabledIfSystemProperty(named = "spa.e2e.dump", matches = ".+")
class DelistByHdelRealDataE2EManual {

    @Test
    void tombstonesVanishAndReadOutputIsUnchanged() throws Exception {
        Map<String, Object> dump = JsonUtils.decodeJson(
                Files.readString(Path.of(System.getProperty("spa.e2e.dump"))),
                new TypeReference<>() {
                });
        String priceKey = (String) dump.get("priceKey");
        @SuppressWarnings("unchecked")
        Map<String, String> hash = (Map<String, String>) dump.get("hash");
        @SuppressWarnings("unchecked")
        Map<String, String> quotes = (Map<String, String>) dump.get("quotes");
        // priceKey 形如 price:{hotelId}:{occupancy}:{date}
        String[] kp = priceKey.split(":");
        String hotelId = kp[1];
        String date = kp[3];
        String checkout = java.time.LocalDate.parse(date).plusDays(1).toString();

        int port = Integer.parseInt(System.getProperty("spa.e2e.redis.port", "63790"));
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        try {
            RedisUtils redisUtils = new RedisUtils();
            ReflectionTestUtils.setField(redisUtils, "redisTemplate", template);

            PriceCacheServiceImpl service = new PriceCacheServiceImpl();
            ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
            ReflectionTestUtils.setField(service, "priceCacheTrimmer", new PriceCacheTrimmer());
            ReflectionTestUtils.setField(service, "abnormalPriceGuard", new AbnormalPriceGuard());
            ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());
            ProductAttributeReader attrs = Mockito.mock(ProductAttributeReader.class);
            Mockito.when(attrs.batchGet(Mockito.anyInt(), Mockito.anyList())).thenReturn(Map.of());
            ReflectionTestUtils.setField(service, "productAttributeReader", attrs);

            // ── 播种：生产原样（含墓碑） ──
            template.getConnectionFactory().getConnection().serverCommands().flushDb();
            template.opsForHash().putAll(priceKey, new HashMap<>(hash));
            template.expire(priceKey, 1, TimeUnit.DAYS);
            quotes.forEach((field, json) ->
                    template.opsForValue().set("quote:" + hotelId + ":" + field, json, 3, TimeUnit.DAYS));

            PriceReq req = PriceReq.builder().checkIn(date).checkout(checkout)
                    .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();
            Supplier sup = Supplier.builder().supplierId(10010).sHotelId(hotelId).build();

            // ── A：墓碑在场时的读侧产出 ──
            Map<String, String> readA = snapshot(service.getPrice(req, sup));
            assertFalse(readA.isEmpty(), "A 读产出为空——dump 里的 quote 详情没配上");

            // ── 新写侧跑一轮：本轮在售 = 有 quote 的那批（其余 live field 即"本轮缺席"）──
            List<ProductRespDTO> round = new ArrayList<>();
            for (Map.Entry<String, String> q : quotes.entrySet()) {
                ProductRespCacheDTO cached = JsonUtils.decodeJson(q.getValue(), new TypeReference<>() {
                });
                ProductRespDTO dto = new ProductRespDTO();
                BeanUtils.copyProperties(cached, dto);
                dto.setHotelId(hotelId);
                dto.setProductKey(q.getKey());
                Map<String, Integer> priceJson = JsonUtils.decodeJson(hash.get(q.getKey()),
                        new TypeReference<>() {
                        });
                dto.setPriceInfos(List.of(PriceInfo.builder().date(date)
                        .price(priceJson.get("price"))
                        .taxes(priceJson.get("taxes"))
                        .roomPrice(priceJson.get("roomPrice")).build()));
                round.add(dto);
            }
            service.productToCache(round, req, sup);

            // ── B：读侧产出必须与 A 逐字段相等 ──
            assertEquals(readA, snapshot(service.getPrice(req, sup)),
                    "换了下架方式，读侧产出必须一字不差");

            // ── 缓存状态：墓碑与缺席者全部消失，且再无 price:0 ──
            Map<Object, Object> after = template.opsForHash().entries(priceKey);
            assertEquals(quotes.size(), after.size(),
                    "该键应只剩本轮在售的 field：" + after.keySet());
            after.values().forEach(v ->
                    assertFalse(((String) v).contains("\"price\":0"), "不得再有墓碑：" + v));
            long tombsBefore = hash.values().stream().filter(v -> v.contains("\"price\":0")).count();
            assertTrue(tombsBefore >= 3, "dump 应含真墓碑，实际 " + tombsBefore);

            System.out.printf("[e2e] key=%s 播种 field=%d(墓碑 %d) → 一轮后剩 %d；A=B 产品 %d 个%n",
                    priceKey, hash.size(), tombsBefore, after.size(), readA.size());
        } finally {
            factory.destroy();
        }
    }

    /** 读侧产出的可比快照：productKey →「总价|productId」 */
    private static Map<String, String> snapshot(List<ProductRespDTO> products) {
        Map<String, String> snap = new TreeMap<>();
        for (ProductRespDTO p : products) {
            snap.put(p.getProductKey(), p.getTotalPrice() + "|" + p.getProductId());
        }
        return snap;
    }
}
