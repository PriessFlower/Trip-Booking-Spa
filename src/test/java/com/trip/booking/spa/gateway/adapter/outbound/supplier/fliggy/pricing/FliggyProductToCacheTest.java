package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogService;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.AbnormalPriceGuard;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheTrimmer;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheTtlPolicy;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import com.trip.booking.spa.platform.redis.RedisUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 真实报文的报价必须落进价格 Hash——从原始 JSON 一路走到 Redis 写入捕获。
 *
 * <p>为什么要跨两个单元钉：convertRates 与 productToCache 各自的单测都绿过，
 * 而生产飞猪缓存 43,166 个键 100% 是无货标记、0 条真价（2026-08-28 实证，
 * 6 小时 103,104 次转换全部出报>0 却零写入）——两者之间的契约
 * （productToCache 只认 priceInfos）没有任何一条测试覆盖。
 */
class FliggyProductToCacheTest {

    private FliggyPriceServiceImpl fliggyService;
    private PriceCacheServiceImpl cacheService;
    private RedisUtils redisUtils;
    private ProductCatalogMapper catalogMapper;

    @BeforeEach
    void setUp() {
        fliggyService = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(fliggyService, "properties", properties);
        ReflectionTestUtils.setField(fliggyService, "productKeyDeriver", new FliggyProductKeyDeriver(properties));

        cacheService = new PriceCacheServiceImpl();
        // 建档走真实通用服务（飞猪开闸 + mock mapper）：钉"真实报文→写缓存漏斗→档案表"整条链
        catalogMapper = Mockito.mock(ProductCatalogMapper.class);
        ProductCatalogService catalogService = new ProductCatalogService();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("supplier.fliggy.catalog-enabled", "true");
        ReflectionTestUtils.setField(catalogService, "productCatalogMapper", catalogMapper);
        ReflectionTestUtils.setField(catalogService, "environment", environment);
        ReflectionTestUtils.setField(cacheService, "productCatalogService", catalogService);
        redisUtils = Mockito.mock(RedisUtils.class);
        ReflectionTestUtils.setField(cacheService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(cacheService, "productAttributeReader",
                Mockito.mock(ProductAttributeReader.class));
        ReflectionTestUtils.setField(cacheService, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(cacheService, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(cacheService, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());

        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(new SimpleMeterRegistry());
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    @Test
    @DisplayName("真实 ari 报文 → 转换 → 写缓存：价格 Hash 必须出现真实 field，不是无货标记")
    void realPayloadPricesLandInPriceHash() throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);
        PriceReq req = PriceReq.builder().checkIn("2026-09-10").checkout("2026-09-11")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(List.of("2"));
        Supplier supplier = Supplier.builder().supplierId(10015).sHotelId("50363404").build();

        List<ProductRespDTO> products = fliggyService.convertRates(resp.rates(), req, "50363404");
        assertFalse(products.isEmpty());
        cacheService.productToCache(products, req, supplier);

        ArgumentCaptor<Map<String, Map<String, String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils).batchHashMapSetWithExpire(cap.capture(), anyLong(), any(TimeUnit.class));
        Map<String, String> fields = cap.getValue().get("price:10015:50363404:2:2026-09-10");

        assertTrue(fields != null && !fields.isEmpty(),
                "在售报价写缓存必须落到该店该日的价格 Hash，实际写入: " + cap.getValue().keySet());
        assertFalse(fields.containsKey("__no_inventory__"), "有货不许打无货标记");
        // field=productKey(64 位 hex)，值是逐日价 JSON，price=含税分价原样
        Map.Entry<String, String> entry = fields.entrySet().iterator().next();
        assertEquals(64, entry.getKey().length());
        assertTrue(entry.getValue().contains("\"price\":10524"),
                "逐日价 JSON 必须带含税分价，实际: " + entry.getValue());
    }

    @Test
    @DisplayName("真实 ari 报文 → 写缓存漏斗 → 档案表：飞猪建档走通用链路自动覆盖")
    void realPayloadProductsLandInCatalog() throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);
        PriceReq req = PriceReq.builder().checkIn("2026-09-10").checkout("2026-09-11")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(List.of("2"));
        Supplier supplier = Supplier.builder().supplierId(10015).sHotelId("50363404").build();

        List<ProductRespDTO> products = fliggyService.convertRates(resp.rates(), req, "50363404");
        cacheService.productToCache(products, req, supplier);

        ArgumentCaptor<java.util.HashMap<String, Object>> cap =
                ArgumentCaptor.forClass(java.util.HashMap.class);
        Mockito.verify(catalogMapper, Mockito.atLeastOnce()).upsertSupplierProductBase(cap.capture());
        java.util.HashMap<String, Object> row = cap.getValue();
        assertEquals(10015, row.get("supplierId"));
        assertEquals("fliggy-refresh", row.get("operator"));
        assertEquals("143328954", row.get("supplierRoomId"), "对照表接的就是这一列——它是建档存在的意义");
        assertEquals("B0L0D0", row.get("mealSignature"), "type 0=正面声明无餐,是已知不是 UNKNOWN");
        assertEquals("FREE_CANCELLABLE", row.get("cancelClass"),
                "真实报文罚金 0 的段=免费窗;若为 UNKNOWN 说明数字串解析又坏了,产品会被 R-5.4 挡在目录外");
    }
}
