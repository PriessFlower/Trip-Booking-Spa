package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「建档随刷价落库」的真实数据 e2e（§2.2.1/§2.2.2：真链路、真数据，非 mock）。
 * <b>手动跑，CI 不含</b>：
 *
 * <pre>
 * docker run -d --name spa-e2e-mysql-catalog -e MYSQL_ROOT_PASSWORD=e2e \
 *   -e MYSQL_DATABASE=tg_trip_spa -p 33070:3306 mysql:8
 * docker exec -i spa-e2e-mysql-catalog mysql -uroot -pe2e tg_trip_spa \
 *   &lt; config/mysql/spa-catalog-schema.sql
 *
 * mvn test -Dtest=ExpediaCatalogFromRefreshE2EManual \
 *   -Dspa.e2e.expedia.key=... -Dspa.e2e.expedia.secret=... \
 *   -Dspa.e2e.expedia.host=https://test.ean.com \
 *   -Dspa.e2e.jdbc=jdbc:mysql://127.0.0.1:33070/tg_trip_spa \
 *   -Dspa.e2e.propertyIds=15714685,9679575
 * </pre>
 *
 * <p><b>真到什么程度</b>：驱动的是生产同一个方法 {@link ExpediaPriceServiceImpl#queryPricesCache}
 * ——也就是本次改动加那行 {@code expediaCatalogService.upsert(...)} 所在的方法；报价来自
 * test.ean.com 的实时响应（生产 {@code EXPEDIA_API_HOST} 指的就是它）；转换、派生、建档、
 * MyBatis、MySQL 全部是真的。<b>唯一替身是 {@code PriceCacheService}</b>（Redis 写缓存），
 * 因为本次没动它，且它不在待验证的因果链上。
 *
 * <p><b>同数据 A/B</b>（§2.2.2）：同一批真实报价跑两遍——A 闸口关、B 闸口开。
 * 断言 A 一行不写、B 写入且 {@code cancel_class} 取值合法。这一对同时钉住两件事：
 * 闸口真的能拦住，以及<b>那行接线真的被执行到</b>——后者是单元测试挡不住的
 * （删掉接线，B 也会变成 0 行，本测试转红）。
 */
@EnabledIfSystemProperty(named = "spa.e2e.jdbc", matches = ".+")
class ExpediaCatalogFromRefreshE2EManual {

    private static final String JDBC = System.getProperty("spa.e2e.jdbc");
    private static final String USER = System.getProperty("spa.e2e.jdbc.user", "root");
    private static final String PASS = System.getProperty("spa.e2e.jdbc.password", "e2e");

    @Test
    void catalogRowsAppearOnlyWhenGateIsOpen() throws Exception {
        // access 对象是 new 出来的、注入不进去，限流走静态桥；不装则 NPE。
        // 用途桶一律"未登记"→按接口桶跑，e2e 只打个位数请求，不需要真限流。
        com.trip.booking.spa.platform.ratelimit.RateLimitManager limitManager =
                Mockito.mock(com.trip.booking.spa.platform.ratelimit.RateLimitManager.class);
        Mockito.when(limitManager.isRegistered(Mockito.anyString())).thenReturn(false);
        ReflectionTestUtils.setField(
                com.trip.booking.spa.platform.ratelimit.RateLimitHolder.class, "manager", limitManager);

        SqlSessionFactory factory = mybatis();
        List<String> propertyIds = List.of(
                System.getProperty("spa.e2e.propertyIds", "15714685").split(","));

        try (SqlSession sessionA = factory.openSession(true);
             SqlSession sessionB = factory.openSession(true)) {

            truncateCatalog();

            // ---------- A：闸口关 ----------
            ExpediaPriceServiceImpl priceServiceA = priceService(sessionA.getMapper(ProductCatalogMapper.class), false);
            List<ProductRespDTO> quotedA = new ArrayList<>();
            for (String pid : propertyIds) {
                List<ProductRespDTO> got = priceServiceA.queryPricesCache(priceReq(), supplier(pid));
                if (got != null) {
                    quotedA.addAll(got);
                }
            }
            long rowsAfterA = catalogRows();

            // 报价必须真的拿到了，否则 A/B 都是 0 行、本测试会"绿得毫无意义"
            assertFalse(quotedA.isEmpty(),
                    "没从 test.ean.com 拿到任何真实报价——凭据/端点/住期先查清楚，"
                            + "否则下面的 0 行不能证明闸口有效");
            assertEquals(0L, rowsAfterA, "闸口关时一行都不该写");

            // ---------- B：闸口开，同一批酒店、同一住期 ----------
            ExpediaPriceServiceImpl priceServiceB = priceService(sessionB.getMapper(ProductCatalogMapper.class), true);
            List<ProductRespDTO> quotedB = new ArrayList<>();
            for (String pid : propertyIds) {
                List<ProductRespDTO> got = priceServiceB.queryPricesCache(priceReq(), supplier(pid));
                if (got != null) {
                    quotedB.addAll(got);
                }
            }
            long rowsAfterB = catalogRows();

            assertFalse(quotedB.isEmpty(), "B 轮没拿到报价，A/B 不同数据则结论不成立");
            assertTrue(rowsAfterB > 0,
                    "闸口开、且确实拿到了 " + quotedB.size() + " 条报价，目录却still 0 行——"
                            + "刷价链路里的 expediaCatalogService.upsert 没有被执行到");

            // 落库的 cancel_class 只能是三分类之一；UNKNOWN 不该出现（R-5.4：不进目录）
            Map<String, Long> byClass = catalogCountByCancelClass();
            assertFalse(byClass.isEmpty(), "写了行却读不出分类，说明列没落对");
            for (String cls : byClass.keySet()) {
                assertTrue("FREE_CANCELLABLE".equals(cls) || "NON_REFUNDABLE".equals(cls),
                        "目录里出现了非法退改类：" + cls + "（UNKNOWN 不得进目录，R-5.4）");
            }

            System.out.println("[e2e] 真实报价 A=" + quotedA.size() + " 条 / B=" + quotedB.size() + " 条；"
                    + "闸口关后目录 " + rowsAfterA + " 行，闸口开后 " + rowsAfterB + " 行；分类分布=" + byClass);
        }
    }

    /** 除 PriceCacheService（Redis，本次未改动）外全部为真实实现 */
    private ExpediaPriceServiceImpl priceService(ProductCatalogMapper mapper, boolean gateOpen) {
        ExpediaRapidProperties props = new ExpediaRapidProperties();
        props.setApiKey(System.getProperty("spa.e2e.expedia.key"));
        ReflectionTestUtils.setField(props, "sharedSecret", System.getProperty("spa.e2e.expedia.secret"));

        ExpediaContractProfile profile = new ExpediaContractProfile();
        // 取值与生产启动日志一致：B2B(partner_point_of_sale=B2B_SA_PKG_MOD_AGENT,
        // billing_terms=EAC, payment_terms=2, sales_channel=agent_tool)
        ReflectionTestUtils.setField(profile, "partnerPointOfSale", "B2B_SA_PKG_MOD_AGENT");
        ReflectionTestUtils.setField(profile, "billingTerms", "EAC");
        ReflectionTestUtils.setField(profile, "paymentTerms", "2");
        ReflectionTestUtils.setField(profile, "salesChannel", "agent_tool");
        ReflectionTestUtils.setField(profile, "rapidProperties", props);

        ExpediaProductKeyDeriver deriver = new ExpediaProductKeyDeriver();
        deriver.setContractProfile(profile);

        ExpediaCatalogService catalog = new ExpediaCatalogService();
        ReflectionTestUtils.setField(catalog, "productCatalogMapper", mapper);
        ReflectionTestUtils.setField(catalog, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(catalog, "catalogEnabled", gateOpen);

        ExpediaPriceServiceImpl svc = new ExpediaPriceServiceImpl();
        ReflectionTestUtils.setField(svc, "host", System.getProperty("spa.e2e.expedia.host", "https://test.ean.com"));
        ReflectionTestUtils.setField(svc, "ownIp", "127.0.0.1");
        ReflectionTestUtils.setField(svc, "sessionId", "spa-e2e");
        ReflectionTestUtils.setField(svc, "expediaUtils", new ExpediaUtils(props));
        ReflectionTestUtils.setField(svc, "rapidProperties", props);
        ReflectionTestUtils.setField(svc, "contractProfile", profile);
        ReflectionTestUtils.setField(svc, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(svc, "expediaCatalogService", catalog);
        // 本次未改动写缓存，且它需要 Redis——唯一的替身，不在待验证的因果链上
        ReflectionTestUtils.setField(svc, "priceCacheService", Mockito.mock(PriceCacheService.class));
        ReflectionTestUtils.setField(svc, "rateLimiter", Mockito.mock(DistributedRateLimiter.class));
        return svc;
    }

    private static PriceReq priceReq() {
        LocalDate checkIn = LocalDate.now().plusDays(9);
        return PriceReq.builder()
                .adultNum(2).childNum(0).childAges(new ArrayList<>())
                .checkIn(checkIn.toString()).checkout(checkIn.plusDays(1).toString())
                .roomNum(1).build();
    }

    private static Supplier supplier(String propertyId) {
        return Supplier.builder().sHotelId(propertyId).build();
    }

    private SqlSessionFactory mybatis() throws Exception {
        PooledDataSource ds = new PooledDataSource("com.mysql.cj.jdbc.Driver", JDBC, USER, PASS);
        Configuration cfg = new Configuration(new Environment("e2e", new JdbcTransactionFactory(), ds));
        cfg.setMapUnderscoreToCamelCase(true);
        try (InputStream in = getClass().getResourceAsStream("/mapper/ProductCatalogMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    in, cfg, "mapper/ProductCatalogMapper.xml", cfg.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(cfg);
    }

    private void truncateCatalog() throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
             Statement s = c.createStatement()) {
            s.execute("TRUNCATE TABLE supplier_product_base");
        }
    }

    private long catalogRows() throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM supplier_product_base WHERE supplier_id=10005")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private Map<String, Long> catalogCountByCancelClass() throws Exception {
        Map<String, Long> out = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(JDBC, USER, PASS);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT cancel_class, COUNT(*) FROM supplier_product_base "
                             + "WHERE supplier_id=10005 GROUP BY cancel_class")) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }
}
