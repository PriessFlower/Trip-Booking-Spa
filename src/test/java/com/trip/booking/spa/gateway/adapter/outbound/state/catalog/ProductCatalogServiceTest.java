package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 供应商通用建档的写入判据（R-2.6 / R-2.3 / R-5.4 / R-2.8）。
 *
 * <p>前身 ElongCatalogService 的场景全部迁入；新增的通用性场景钉三件事：
 * 判决读自 identity 成分（不问 deriver、不重判 DTO）、开关按家各管各的、
 * 枚举外的旧代码供应商静默跳过。
 */
class ProductCatalogServiceTest {

    private ProductCatalogService service;
    private ProductCatalogMapper mapper;
    private MockEnvironment environment;
    private ElongProductKeyDeriver elongDeriver;
    private FliggyProductKeyDeriver fliggyDeriver;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService();
        mapper = Mockito.mock(ProductCatalogMapper.class);
        environment = new MockEnvironment();
        environment.setProperty("supplier.elong.catalog-enabled", "true");
        ReflectionTestUtils.setField(service, "productCatalogMapper", mapper);
        ReflectionTestUtils.setField(service, "environment", environment);

        elongDeriver = new ElongProductKeyDeriver();
        ElongProperties elongProps = new ElongProperties();
        ReflectionTestUtils.setField(elongProps, "user", "tgtrip");
        ReflectionTestUtils.setField(elongDeriver, "properties", elongProps);

        FliggyProperties fliggyProps = new FliggyProperties();
        fliggyProps.setAppKey("app-1");
        fliggyDeriver = new FliggyProductKeyDeriver(fliggyProps);
    }

    /**
     * 产品必须携带 identity——建档只照抄它（R-2.8）。identity 用与生产同一条路径
     * （deriver）算出，而不是手工拼，否则测试就成了"自己发明一套成分"。
     */
    private ProductRespDTO elongProduct(Meal meal, List<CancelPolicy> cancel) {
        ProductIdentity identity = elongDeriver.deriveIdentity("61832733", "0033", meal, cancel, "2", 20000);
        return ProductRespDTO.builder()
                .hotelId("61832733").productId("62022758A19A7133205")
                .productKey(identity.productKey()).identity(identity)
                .room(Room.builder().roomId("0033").roomName("大床房").build())
                .meal(meal).cancelPolicy(cancel)
                .build();
    }

    private static Supplier elong() {
        return Supplier.builder().supplierId(10010).sHotelId("61832733").build();
    }

    private static Meal breakfast() {
        Meal m = new Meal(); m.setCount(2); m.setLunchCount(0); m.setDinnerCount(0); return m;
    }

    private static List<CancelPolicy> freeCancel() {
        return List.of(CancelPolicy.builder().cancelType(1).type(RefundType.NO_DEDUCTION).before(36).build());
    }

    /** 落库的每一列都必须原样来自 identity（R-2.8），且成分不得降维（R-2.7） */
    @Test
    void everyColumnIsCopiedFromIdentityVerbatim() {
        ProductRespDTO p0 = elongProduct(breakfast(), freeCancel());
        service.upsert(List.of(p0), elong());

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        HashMap<String, Object> p = cap.getValue();
        ProductIdentity id = p0.getIdentity();

        assertEquals(id.productKey(), p.get("productKey"), "身份列必须是 productKey，不是供应商报价码");
        assertEquals(id.account(), p.get("supplierAccount"));
        assertEquals(id.supplierHotelId(), p.get("supplierHotelId"));
        assertEquals(id.supplierRoomId(), p.get("supplierRoomId"));
        assertEquals(id.mealSignature(), p.get("mealSignature"));
        assertEquals(id.cancelClass(), p.get("cancelClass"));
        assertEquals(id.occupancy(), p.get("occupancy"));
        assertEquals(10010, p.get("supplierId"));
        assertEquals("elong-refresh", p.get("operator"));
        assertNull(p.get("supplierQuoteHint"), "艺龙报价码易腐,禁止落 hint 列(R-2.3)——否则又成'从库里读凭证'");

        // R-2.9：房型层与聚合域的列已随表重设计移除，不得再出现在写入载荷里
        assertNull(p.get("hasWindow"));
        assertNull(p.get("supplierBedDesc"));
        assertNull(p.get("productId"));
        assertNull(p.get("roomId"));
        assertNull(p.get("hotelId"));
        // 降维过的旧列必须彻底消失，否则新旧口径并存又会分叉
        assertNull(p.get("breakfast"));
        assertNull(p.get("cancelType"));
    }

    /** 含三餐与只含早必须落成不同的成分——这正是旧的 breakfast 0/1 分不出来的那一对 */
    @Test
    void fullBoardAndBreakfastOnlyLandDifferently() {
        Meal fullBoard = new Meal();
        fullBoard.setCount(2); fullBoard.setLunchCount(2); fullBoard.setDinnerCount(2);

        service.upsert(List.of(elongProduct(fullBoard, freeCancel())), elong());
        service.upsert(List.of(elongProduct(breakfast(), freeCancel())), elong());

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper, Mockito.times(2)).upsertSupplierProductBase(cap.capture());
        assertEquals("B1L1D1", cap.getAllValues().get(0).get("mealSignature"));
        assertEquals("B1L0D0", cap.getAllValues().get(1).get("mealSignature"));
    }

    /**
     * 退改解析不出(空列表)→ c:UNKNOWN → 不进目录(R-5.4)。
     * 这类产品的 productKey 是【合法非空】的，实时链路照常可售——所以建档侧不能只判
     * "key 是否为空"，必须读 identity 里 deriver 写下的判决。
     */
    @Test
    void unknownCancelPolicyNeverEntersCatalog() {
        service.upsert(List.of(elongProduct(breakfast(), List.of())), elong());
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 餐食解析不出 → m:UNKNOWN → 同样不进目录 */
    @Test
    void unknownMealNeverEntersCatalog() {
        service.upsert(List.of(elongProduct(null, freeCancel())), elong());
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /**
     * 可取消但全程收费 = 第三种 UNKNOWN，从出参 DTO 表面看不出来——deriver 判完
     * 写进 identity.cancelClass，建档读成分即被挡住。这正是"读判决不重判"要钉住的点。
     */
    @Test
    void cancellableButAlwaysChargedIsUnknownToo() {
        List<CancelPolicy> allCharged = List.of(
                CancelPolicy.builder().cancelType(1).type(RefundType.DEDUCT_BY_AMOUNT).amount(5000).before(36).build());
        service.upsert(List.of(elongProduct(breakfast(), allCharged)), elong());
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /**
     * 每段<b>确定</b>罚≥全款 → NON_REFUNDABLE，可进目录（双家同判据，判规则内容不猜码表；
     * 飞猪 305 条采样实证 122/122，艺龙 CutType=4 官方语义即全额房费）。
     * 罚部分的仍归 UNKNOWN——由上一条测试看住，两条合起来钉死判据的边界。
     */
    @Test
    void fullPenaltyIsNonRefundableAndEntersCatalog() {
        // 艺龙形态一：比例 100%（CutType=4 全额房费即转成它）——不依赖总价
        List<CancelPolicy> fullPercent = List.of(CancelPolicy.builder()
                .cancelType(1).type(RefundType.DEDUCT_BY_PERCENT).value(100D).before(36).build());
        // 艺龙形态二：定额（元）≥ 总价（elongProduct 的总价=20000 分）
        List<CancelPolicy> fullAmount = List.of(CancelPolicy.builder()
                .cancelType(1).type(RefundType.DEDUCT_BY_AMOUNT).value(200D).before(36).build());

        service.upsert(List.of(elongProduct(breakfast(), fullPercent)), elong());
        service.upsert(List.of(elongProduct(breakfast(), fullAmount)), elong());

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper, Mockito.times(2)).upsertSupplierProductBase(cap.capture());
        assertEquals("NON_REFUNDABLE", cap.getAllValues().get(0).get("cancelClass"));
        assertEquals("NON_REFUNDABLE", cap.getAllValues().get(1).get("cancelClass"));
    }

    /** 开关按家：没配置的家默认关，不得写库（哪怕别家开着） */
    @Test
    @DisplayName("飞猪开关未配置 → 默认关,一个字节不写")
    void switchDefaultsOffPerSupplier() {
        service.upsert(List.of(fliggyProduct()), fliggy());
        Mockito.verifyNoInteractions(mapper);
    }

    private ProductRespDTO fliggyProduct() {
        Meal meal = new Meal(); meal.count = 0; meal.lunchCount = 0; meal.dinnerCount = 0;
        ProductIdentity identity = fliggyDeriver.deriveIdentity("50363404", "143328954",
                meal, freeCancel(), "2", 20000);
        return ProductRespDTO.builder()
                .hotelId("50363404").productId("V3|rate-key-1")
                .productKey(identity.productKey()).identity(identity)
                .supplierId(10015)
                .build();
    }

    private static Supplier fliggy() {
        return Supplier.builder().supplierId(10015).sHotelId("50363404").build();
    }

    /** 飞猪走同一条通用链路：supplierId/operator 按家、易腐码 hint 恒 null（PERISHABLE 申报） */
    @Test
    void fliggyLandsThroughTheSameGenericPath() {
        environment.setProperty("supplier.fliggy.catalog-enabled", "true");

        service.upsert(List.of(fliggyProduct()), fliggy());

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        HashMap<String, Object> p = cap.getValue();
        assertEquals(10015, p.get("supplierId"));
        assertEquals("fliggy-refresh", p.get("operator"));
        assertEquals("app-1", p.get("supplierAccount"));
        assertEquals("143328954", p.get("supplierRoomId"));
        assertEquals("B0L0D0", p.get("mealSignature"), "type 0=正面声明无餐,是已知不是 UNKNOWN");
        assertNull(p.get("supplierQuoteHint"), "飞猪 rate_key 申报易腐(PERISHABLE),禁落 hint");
    }

    /** 枚举外的旧代码供应商：无申报无开关键，静默跳过不抛 */
    @Test
    void legacySupplierCodeIsSkippedEntirely() {
        ProductRespDTO p = elongProduct(breakfast(), freeCancel());
        p.setSupplierId(9999);
        assertDoesNotThrow(() -> service.upsert(List.of(p),
                Supplier.builder().supplierId(9999).sHotelId("H1").build()));
        Mockito.verifyNoInteractions(mapper);
    }

    /** 建档是增益路径：写库抛异常不得打断报价主路径 */
    @Test
    void writeFailureNeverBreaksRefresh() {
        Mockito.when(mapper.upsertSupplierProductBase(Mockito.any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.upsert(List.of(elongProduct(breakfast(), freeCancel())), elong()));
    }
}
