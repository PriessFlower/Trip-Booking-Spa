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
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 飞猪刷价<b>真链路</b> e2e（§2.2.2 真库真数据非 mock）：真凭据打真飞猪 →
 * 转换 → 真 Redis 价格缓存 → 真 MySQL 档案表 → 读侧出价回查档案补房型。<b>手动跑</b>，CI 不含：
 *
 * <pre>
 * docker run -d --name spa-e2e-redis-fliggy -p 63791:6379 redis:7-alpine
 * docker run -d --name spa-e2e-mysql-fliggy -p 33061:3306 \
 *   -e MYSQL_ROOT_PASSWORD=e2e -e MYSQL_DATABASE=tg_trip_spa mysql:8
 * set -a; . .env; set +a   # FLIGGY_* 六个
 * FLIGGY_E2E=1 mvn test -Dtest=FliggyRefreshRealE2EManual
 * </pre>
 *
 * <p>为什么必须有它：写价（priceInfos 空置，43,166 键 100% 无货标记）和退改
 * （数字串解析丢光规则）两个静默事故都发生在"各单元测试全绿"的地方——只有把真响应
 * 从头走到真存储，形状漂移才无处可藏。
 */
@EnabledIfEnvironmentVariable(named = "FLIGGY_E2E", matches = "1")
class FliggyRefreshRealE2EManual {

    /** 新宿华盛顿：2026-08-27 实证在售（22 条报价），首笔真实调用与真报文快照同源 */
    private static final String HOTEL = "50363404";

    private static final String JDBC = "jdbc:mysql://127.0.0.1:33061/tg_trip_spa"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";

    private static LettuceConnectionFactory redisFactory;

    @AfterAll
    static void closeRedis() {
        if (redisFactory != null) {
            redisFactory.destroy();
        }
    }

    @Test
    @DisplayName("真凭据查价 → 真 Redis 有真价 → 真 MySQL 有档案 → 读侧出价带房型")
    void refreshLandsInRealStores() throws Exception {
        // ── 凭据与限流中枢（同艺龙 e2e：没起容器，手动装全放行实现，否则通道层 NPE）──
        FliggyProperties props = new FliggyProperties();
        props.setAppKey(System.getenv("FLIGGY_APP_KEY"));
        set(props, "secret", System.getenv("FLIGGY_SECRET"));
        set(props, "session", System.getenv("FLIGGY_SESSION"));
        set(props, "distributor", System.getenv("FLIGGY_DISTRIBUTOR"));
        String host = System.getenv("FLIGGY_API_HOST");
        set(props, "urlHost", host == null || host.isBlank() ? "https://eco.taobao.com/router/rest" : host);
        assertTrue(props.isConfigured(), "缺 FLIGGY_APP_KEY/FLIGGY_SECRET/FLIGGY_SESSION（先 source .env）");

        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override public void acquire(String key) { }
            @Override public boolean tryAcquire(String key) { return true; }
            @Override public boolean isRegistered(String key) { return true; }
        });
        holder.setApplicationContext(ctx);

        // ── 真 Redis ──
        redisFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 63791));
        redisFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(redisFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        RedisUtils redisUtils = new RedisUtils();
        ReflectionTestUtils.setField(redisUtils, "redisTemplate", template);

        // ── 真 MySQL：用仓里的 DDL 原文建档案表（手抄一份 schema 就守不住漂移了）──
        try (Connection c = DriverManager.getConnection(JDBC, "root", "e2e");
             Statement st = c.createStatement()) {
            String schema = Files.readString(Path.of("config/mysql/spa-catalog-schema.sql"));
            int from = schema.indexOf("CREATE TABLE IF NOT EXISTS supplier_product_base");
            // 列 COMMENT 里有 ASCII 分号，语句终结符只能认 ENGINE 行之后那个
            int engine = schema.indexOf("ENGINE=InnoDB", from);
            st.execute(schema.substring(from, schema.indexOf(';', engine)));
            st.execute("TRUNCATE TABLE supplier_product_base");
        }
        Configuration cfg = new Configuration(new org.apache.ibatis.mapping.Environment(
                "e2e", new JdbcTransactionFactory(), new UnpooledDataSource(
                        "com.mysql.cj.jdbc.Driver", JDBC, "root", "e2e")));
        new XMLMapperBuilder(Resources.getResourceAsStream("mapper/ProductCatalogMapper.xml"),
                cfg, "mapper/ProductCatalogMapper.xml", cfg.getSqlFragments()).parse();
        SqlSession sql = new SqlSessionFactoryBuilder().build(cfg).openSession(true);
        ProductCatalogMapper catalogMapper = sql.getMapper(ProductCatalogMapper.class);

        // ── 真服务全链装配 ──
        ProductCatalogService catalogService = new ProductCatalogService();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("supplier.fliggy.catalog-enabled", "true");
        ReflectionTestUtils.setField(catalogService, "productCatalogMapper", catalogMapper);
        ReflectionTestUtils.setField(catalogService, "environment", environment);

        ProductAttributeReader attributeReader = new ProductAttributeReader();
        ReflectionTestUtils.setField(attributeReader, "productCatalogMapper", catalogMapper);
        ReflectionTestUtils.setField(attributeReader, "maxSize", 50000);

        PriceCacheServiceImpl cacheService = new PriceCacheServiceImpl();
        ReflectionTestUtils.setField(cacheService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(cacheService, "productCatalogService", catalogService);
        ReflectionTestUtils.setField(cacheService, "productAttributeReader", attributeReader);
        ReflectionTestUtils.setField(cacheService, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(cacheService, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(cacheService, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());

        FliggyPriceServiceImpl fliggyService = new FliggyPriceServiceImpl();
        ReflectionTestUtils.setField(fliggyService, "properties", props);
        ReflectionTestUtils.setField(fliggyService, "productKeyDeriver", new FliggyProductKeyDeriver(props));
        ReflectionTestUtils.setField(fliggyService, "priceCacheService", cacheService);

        // ── 刷价（口径同生产：飞猪按北京时间计入住日，T+13 单晚，2 人占用）──
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(13);
        PriceReq req = PriceReq.builder()
                .checkIn(checkIn.toString()).checkout(checkIn.plusDays(1).toString())
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        Supplier supplier = Supplier.builder().supplierId(10015).sHotelId(HOTEL).build();

        List<ProductRespDTO> products = fliggyService.queryPricesCache(req, supplier);

        assertNotNull(products, "查价未取得结果——网络或凭据病（session 到期看 [auth-config] 日志）");
        assertFalse(products.isEmpty(), "新宿华盛顿 T+13 报全无货——极不寻常，先人工核实再怀疑测试");

        // ── 真 Redis：价格 Hash 有真 field（不是无货标记），分价>0 ──
        String priceKey = "price:10015:" + HOTEL + ":2:" + checkIn;
        Map<Object, Object> hash = template.opsForHash().entries(priceKey);
        assertFalse(hash.isEmpty(), "价格 Hash 没写进 Redis: " + priceKey);
        assertFalse(hash.containsKey("__no_inventory__"), "有货不许打无货标记");
        String field = (String) hash.keySet().iterator().next();
        assertEquals(64, field.length(), "field 必须是 productKey(64 hex)");
        assertTrue(((String) hash.get(field)).matches(".*\"price\":[1-9]\\d*.*"),
                "逐日价 JSON 必须带正分价,实际: " + hash.get(field));
        assertTrue(Boolean.TRUE.equals(template.hasKey("quote:10015:" + HOTEL + ":" + field)),
                "票据键缺席——只有价没有票不可成交");

        // ── 真 MySQL：档案表有该店的行，成分列全非 UNKNOWN ──
        int rows = 0;
        try (Connection c = DriverManager.getConnection(JDBC, "root", "e2e");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT supplier_room_id, meal_signature, cancel_class"
                     + " FROM supplier_product_base WHERE supplier_id=10015 AND supplier_hotel_id='" + HOTEL + "'")) {
            while (rs.next()) {
                rows++;
                assertFalse(rs.getString("supplier_room_id").isBlank());
                assertFalse("UNKNOWN".equals(rs.getString("meal_signature")), "UNKNOWN 不许进目录(R-5.4)");
                assertFalse("UNKNOWN".equals(rs.getString("cancel_class")), "UNKNOWN 不许进目录(R-5.4)");
            }
        }
        assertTrue(rows > 0, "档案表零行——建档没走到(开关?判定?),此前飞猪整家如此");

        // ── 读侧全环：出价从缓存来、房型从档案回查来（对照表的接头就在这）。
        // UNKNOWN 成分的产品合法地无档案（R-5.4），房型缺席不删报价（R-1.6）——
        // 故断言"进了目录的那批必须回查得到房型"，而不是"第一条必须有"
        List<ProductRespDTO> served = cacheService.getPrice(req, supplier);
        assertFalse(served.isEmpty(), "缓存出价为空——写读两侧键口径又漂了");
        for (ProductRespDTO p : served) {
            assertTrue(p.getTotalPrice() > 0);
            assertEquals(64, p.getProductKey().length());
        }
        ProductRespDTO archived = served.stream()
                .filter(p -> p.getRoom() != null && p.getRoom().getRoomId() != null)
                .findFirst().orElse(null);
        assertNotNull(archived, "没有任何出价带房型——档案回查没接上,建档等于白建");
        assertFalse(archived.getRoom().getRoomId().isBlank(), "room_id 是对照表的钥匙,必须有");
        assertFalse(archived.getCancelPolicy() == null || archived.getCancelPolicy().isEmpty(),
                "退改条款空——上游会按'退改从严'兜底(艺龙 26,011 事故同款)");

        sql.close();
        System.out.printf("[e2e] 出报 %d 条,缓存 field %d 个,档案 %d 行,带房型出价 %d/%d,示例房型=%s%n",
                products.size(), hash.size(), rows,
                served.stream().filter(p -> p.getRoom() != null).count(), served.size(),
                archived.getRoom().getRoomName());
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
